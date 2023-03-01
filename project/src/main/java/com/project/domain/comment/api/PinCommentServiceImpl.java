package com.project.domain.comment.api;

import com.project.common.exception.EntityNotFoundException;
import com.project.domain.comment.dto.PinCommentDTO;
import com.project.domain.comment.entity.PinComment;
import com.project.domain.comment.repository.PinCommentRepository;
import com.project.domain.pin.entity.Pin;
import com.project.domain.pin.repository.PinRepository;
import com.project.domain.users.entity.Users;
import com.project.domain.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PinCommentServiceImpl implements PinCommentService {

    private final PinRepository pinRepository;
    private final UserRepository userRepository;
    private final PinCommentRepository pinCommentRepository;

    @Override
    @Transactional
    public PinCommentDTO.PinCommentDetailResponse createPinComment(Users user, PinCommentDTO.CreatePinCommentRequest request) {

        Users getUser = userRepository.findByEmail(user.getEmail()).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다."));
        Pin getPin = pinRepository.findById(request.getPinId()).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 Pin 입니다."));
        PinComment pinComment = request.toEntity(getUser, getPin);

        pinComment.setCommentOrder(pinCommentRepository.getLastPinCommentOrder(getPin.getId()) + 1);

        // 부모 댓글은 부모 번호를 자신의 댓글 번호로 한다.
        if (pinComment.getParentCommentId() == -1) {
            pinComment.setParentCommentId(pinComment.getParentCommentId());
        } else {
            long parentCommentId = pinComment.getParentCommentId();
            // 자식 댓글이라면, 부모 댓글의 자식 수를 증가시킨다.
            PinComment parentComm = pinCommentRepository.findByCommentOrder(parentCommentId);
            parentComm.plusChildCommentCount();

        }
        pinCommentRepository.save(pinComment);

        return new PinCommentDTO.PinCommentDetailResponse(pinComment);
    }

    @Override
    public List<PinCommentDTO.PinCommentDetailResponse> getPinCommentByPinId(Long pinId) {

        List<PinComment> pinCommentList = pinCommentRepository.findByPinId(pinId);

        return pinCommentList.stream().map(PinCommentDTO.PinCommentDetailResponse::new).collect(Collectors.toList());

    }

    @Override
    @Transactional
    public void deletePinComment(Long pinCommentId) {

        PinComment pinComment = pinCommentRepository.findById(pinCommentId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 pin댓글 입니다."));
        if (!pinComment.getCommentOrder().equals(pinComment.getParentCommentId())) {
            // 부모 댓글 확인
            PinComment parentPinComment = pinCommentRepository.findByCommentOrder(pinComment.getParentCommentId());
            parentPinComment.minusChildCommentCount();
            // 자식 댓글이 없고, isDeleted = true인 부모 댓글은 삭제시킨다.
            if (parentPinComment.getChildCommentCount() == 0 && parentPinComment.getIsDeleted())
                pinCommentRepository.delete(parentPinComment);

        }
        pinCommentRepository.delete(pinComment);
    }

    @Override
    public void deletePinCommentWithStatus(Long pinCommentId) {
        PinComment comment = pinCommentRepository.findById(pinCommentId).orElseThrow(
                () -> new EntityNotFoundException("해당 pin댓글이 존재하지 않습니다."));

        comment.setDeleted();
    }

    @Override
    public PinCommentDTO.PinCommentDetailResponse updatePinComment(Long pinCommentId, PinCommentDTO.UpdatePinCommentRequest request) {
        PinComment comment = pinCommentRepository.findById(pinCommentId).orElseThrow(
                () -> new EntityNotFoundException("해당 pin댓글이 존재하지 않습니다."));
        comment.setText(request.getText());

        return new PinCommentDTO.PinCommentDetailResponse(comment);
    }


}