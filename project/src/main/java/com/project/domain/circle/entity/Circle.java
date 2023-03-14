package com.project.domain.circle.entity;

import com.project.common.entity.BaseTimeEntity;
import com.project.domain.pin.entity.Pin;
import com.project.domain.usercircle.entity.UserCircle;
import com.project.domain.users.entity.Users;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "circle")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class Circle extends BaseTimeEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @OneToMany(mappedBy = "circle", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @Builder.Default
    private List<Pin> pinList = new ArrayList<>();

    @OneToMany(mappedBy = "circle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserCircle> userCircleList = new ArrayList<>();

    @Column(name = "name")
    private String name;

    public void addPin(Pin pin) {
        this.getPinList().add(pin);
        pin.setCircle(this);
    }

    public void removePin(Pin pin) {
        this.getPinList().remove(pin);
        pin.setCircle(null);
    }

    public void setName(String circleName) {
        this.name = circleName;
    }

    public void addUserCircle(UserCircle userCircle) {
        this.getUserCircleList().add(userCircle);
        userCircle.setCircle(this);
    }

    public void removeUserCircle(UserCircle userCircle) {
        this.getUserCircleList().remove(userCircle);
        userCircle.setCircle(null);
    }

}
