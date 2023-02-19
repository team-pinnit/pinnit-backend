package com.project.album.domain.usercircle.repository;

import com.project.album.domain.usercircle.entity.UserCircle;
import com.project.album.domain.users.entity.Users;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class UserCircleRepositoryCustomImpl implements UserCircleRepositoryCustom {

    private final EntityManager em;

    @Override
    public List<UserCircle> findByUserId(Long userId) {
        return em.createQuery("select uc From UserCircle uc where uc.user.id = :userId", UserCircle.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public List<Users> findUserListByCircleId(Long userId, Long circleId) {
        return em.createQuery("select u From Users u join fetch UserCircle as uc where uc.circle.id = :circleId and uc.user.id = :userId", Users.class)
                .setParameter("userid", userId)
                .setParameter("circleId", circleId)
                .getResultList();
    }

    @Override
    public int exitedUserFromGroup(Long userId, Long circleId) {
        return em.createQuery("delete from UserCircle where user.id = :userId and circle.id = :circleId")
                .setParameter("userId", userId)
                .setParameter("circleId", circleId)
                .executeUpdate();
    }

}
