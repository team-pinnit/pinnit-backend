package com.project.domain.pocket.api;

import com.project.common.exception.BusinessLogicException;
import com.project.common.exception.EntityNotFoundException;
import com.project.common.exception.ErrorCode;
import com.project.common.handler.S3Uploader;
import com.project.domain.pocket.dto.PocketDTO;
import com.project.domain.pocket.entity.Pocket;
import com.project.domain.pocket.repository.PocketRepository;
import com.project.domain.userpocket.entity.UserPocket;
import com.project.domain.userpocket.repository.UserPocketRepository;
import com.project.domain.users.entity.Users;
import com.project.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PocketServiceImpl implements PocketService {

    private final UserPocketRepository userPocketRepository;
    private final PocketRepository pocketRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;

    @Override
    @Transactional
    public PocketDTO.PocketSimpleInfoResponse createPocket(Users user, PocketDTO.CreatePocketRequest request) {
        Pocket pocket = request.toEntity();
        pocket.setPocketKey(pocket.generatePocketKey());
        pocket.setMaster(user);

        UserPocket userPocket = UserPocket.builder().user(user).activated(true).pocket(pocket).build();
        userPocket.addUserPocketToUserAndPocket(user, pocket);

        // 그룹 생성 시, 친구를 같이 초대하는 경우 처리
        if (request.getInvitedUserList() != null && !request.getInvitedUserList().isEmpty()) {
            request.getInvitedUserList().forEach((userId) -> {
                Users u = userRepository.findById(userId).orElseThrow();
                UserPocket uc = UserPocket.builder().pocket(pocket).activated(false).user(u).build();
                uc.addUserPocketToUserAndPocket(u, pocket);
                userPocketRepository.save(uc);
            });
        }
        pocketRepository.save(pocket);

        return new PocketDTO.PocketSimpleInfoResponse(pocket);
    }

    @Override
    public PocketDTO.PocketSimpleInfoListResponse getAllPocketByUser(Long userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new EntityNotFoundException("User does not exist.");
        }

        List<Pocket> pocketList = pocketRepository.findAllPocketByUserId(userId);
        List<PocketDTO.PocketSimpleInfoResponse> response = pocketList.stream().map(PocketDTO.PocketSimpleInfoResponse::new).collect(Collectors.toList());

        return new PocketDTO.PocketSimpleInfoListResponse(response);

    }

    @Override
    public PocketDTO.PocketDetailInfoResponse getPocketDetail(Long pocketId) {
        Pocket pocket = getPocket(pocketId);
        return new PocketDTO.PocketDetailInfoResponse(pocket);
    }

    @Override
    public PocketDTO.PocketWithJoinUserResponse getJoinedUserOfPocket(Long pocketId) {
        Pocket pocket = getPocket(pocketId);
        return new PocketDTO.PocketWithJoinUserResponse(pocket);
    }

    // 본인이 스스로 나감
    @Override
    @Transactional
    public PocketDTO.PocketSimpleInfoResponse leavePocket(Users user, Long pocketId) {
        Pocket pocket = getPocket(pocketId);
        if (isMasterUser(pocket, user.getId()) && pocket.getUserPocketList().size() > 1) {
            throw new BusinessLogicException("Manager cannot leave group", ErrorCode.POCKET_MANAGER_ERROR);
        }

        // 유저가 혼자 남았을 경우에 그룹을 나가게 되면 해당 그룹이 삭제된다.
        if (pocket.getUserPocketList().size() == 1) {
            UserPocket userPocket = userPocketRepository.findByUserIdAndPocketId(user.getId(), pocket.getId()).orElseThrow();
            userPocket.removeUserPocketFromUserAndPocket(user, pocket);
            pocketRepository.delete(pocket);
        } else {
            UserPocket userPocket = userPocketRepository.findByUserIdAndPocketId(user.getId(), pocket.getId()).orElseThrow();
            userPocket.removeUserPocketFromUserAndPocket(user, pocket);
        }

        return new PocketDTO.PocketSimpleInfoResponse(pocket);
    }

    // 방장이 유저를 추방함
    @Override
    @Transactional
    public PocketDTO.PocketSimpleInfoResponse banUserFromPocket(Users user, Long pocketId, PocketDTO.BanUserRequest banUserRequest) {

        Pocket pocket = pocketRepository.findById(pocketId).orElseThrow();
        // 방장 권한일 경우
        if (isMasterUser(pocket, user.getId())) {
            UserPocket userPocket = userPocketRepository.findByUserIdAndPocketId(banUserRequest.getUserId(), pocketId).orElseThrow();
            userPocket.removeUserPocketFromUserAndPocket(user, pocket);
        }
        return new PocketDTO.PocketSimpleInfoResponse(pocket);
    }

    // 그룹에 유저를 초대
    @Override
    @Transactional
    public PocketDTO.InviteUserResponse inviteUser(Users user, Long pocketId, PocketDTO.InviteUserRequest request) {
        Pocket pocket = getPocket(pocketId);
        List<Long> invitedUserList = request.getInvitedUserList();

        for (Long userId : invitedUserList) {
            Users u = getUser(userId);
            Optional<UserPocket> userPocket = userPocketRepository.findByUserIdAndPocketId(u.getId(), pocketId);
            if (userPocket.isPresent()) {
                continue;
            }
            UserPocket uc = UserPocket.builder().user(u).pocket(pocket).activated(false).build(); // activated = false : 수락 이전 상태
            userPocketRepository.save(uc);
        }

        return new PocketDTO.InviteUserResponse(pocket, user);
    }

    // 앱 설치 후 유저가 링크를 타고 들어올 경우, 로그인을 완료했을 경우 그룹 초대를 자동으로 한다.
    @Override
    public PocketDTO.InviteUserFromLinkResponse inviteUserFromLink(Users user, String pocketKey) {
        Pocket pocket = pocketRepository.findPocketByKey(pocketKey);
        UserPocket userPocket = UserPocket.builder().pocket(pocket).user(user).activated(false).build();
        userPocketRepository.save(userPocket);

        return new PocketDTO.InviteUserFromLinkResponse(userPocket);
    }

    // 유저가 초대 요청을 수락
    @Override
    @Transactional
    public PocketDTO.acceptPocketInvitationResponse acceptPocketInvitation(Users user, Long pocketId) {
        Pocket pocket = getPocket(pocketId);
        UserPocket userPocket = userPocketRepository.findByUserIdAndPocketId(user.getId(), pocketId).orElseThrow();
        userPocket.setActivated(userPocket.getActivated());
        userPocket.addUserPocketToUserAndPocket(user, pocket);

        return new PocketDTO.acceptPocketInvitationResponse(user, userPocket);
    }

    @Override
    @Transactional
    public PocketDTO.cancelInvitePocketResponse cancelPocketInvitation(Users user, Long pocketId, Long cancelUserId) {
        Pocket pocket = getPocket(pocketId);
        UserPocket userPocket = userPocketRepository.findByUserIdAndPocketId(cancelUserId, pocketId).orElseThrow(() -> {
            throw new EntityNotFoundException("User does not exists.");
        });
        // 요청을 보내는 유저가 해당 그룹에 속해있어야 초대 취소가 가능하다.
        if (pocket.getUserPocketList().stream()
                .filter(UserPocket::getActivated)
                .map(UserPocket::getUser).toList().contains(user)) {
            userPocket.removeUserPocketFromUserAndPocket(user, pocket);
        }

        return new PocketDTO.cancelInvitePocketResponse(user, pocket);
    }

    @Override
    @Transactional
    public PocketDTO.PocketSimpleInfoResponse updatePocket(Users user, Long pocketId, PocketDTO.UpdatePocketRequest request, MultipartFile picture) {
        Pocket pocket = getPocket(pocketId);
        if (isMasterUser(pocket, user.getId())) {
            pocket.setName(request.getPocketName());
            pocket.setDescription(request.getDescription());
            if (picture != null && !picture.isEmpty()) {
                String imageUrl = s3Uploader.uploadAndSaveImage(picture);
                pocket.setImageUrl(imageUrl);
            }
        } else {
            throw new BusinessLogicException("Only the manager can modify group settings.", ErrorCode.POCKET_MANAGER_ERROR);
        }
        return new PocketDTO.PocketSimpleInfoResponse(pocket);
    }

    // 유저가 방장일 경우, 방장 권한을 위임한다.
    @Override
    @Transactional
    public PocketDTO.PocketWithJoinUserResponse updatePocketMaster(Users user, Long pocketId, Long userId) {
        Pocket pocket = getPocket(pocketId);
        if (isMasterUser(pocket, user.getId())) {
            Users u = userRepository.findById(userId).orElseThrow();
            // 위임하려는 유저가 해당 그룹에 속해 있어야 한다.
            if (!isUserInPocket(u, pocketId)) {
                throw new EntityNotFoundException("User manager delegated does not exists", ErrorCode.POCKET_MANAGER_ERROR);
            }
            pocket.setMaster(u);
        }
        return new PocketDTO.PocketWithJoinUserResponse(pocket);
    }

    @Override
    public PocketDTO.NotAcceptPocketInviteUserResponse getAllNotAcceptPocketInviteUser(Users user, Long pocketId) {
        Pocket pocket = getPocket(pocketId);
        return new PocketDTO.NotAcceptPocketInviteUserResponse(pocket);
    }

    private boolean isMasterUser(Pocket pocket, Long userId) {
        return pocket.getMaster().getId().equals(userId);
    }

    private Pocket getPocket(Long pocketId) {
        return pocketRepository.findById(pocketId).orElseThrow(() -> {
            throw new EntityNotFoundException("Group does not exists.");
        });
    }

    private Users getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> {
            throw new EntityNotFoundException("User does not exists.");
        });
    }

    private boolean isUserInPocket(Users user, Long pocketId) {
        Pocket pocket = getPocket(pocketId);
        return pocket.getUserPocketList().stream()
                .map(UserPocket::getUser).toList().contains(user);
    }
}