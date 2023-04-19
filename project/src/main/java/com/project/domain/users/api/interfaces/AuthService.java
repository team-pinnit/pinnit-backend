package com.project.domain.users.api.interfaces;

import com.project.domain.users.dto.UserDTO;

public interface AuthService {

    UserDTO.SignUpResponse signUp(UserDTO.SignUpRequest signUpRequest) throws Exception;

    UserDTO.LoginResponse login(UserDTO.LoginRequest loginRequest) throws Exception;

    void authEmail(UserDTO.EmailRequest emailRequest) throws Exception;

    Boolean validateAuthEmail(UserDTO.ValidateEmailRequest validateEmailRequest) throws Exception;
}
