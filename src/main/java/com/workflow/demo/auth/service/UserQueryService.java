package com.workflow.demo.auth.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.workflow.demo.auth.dto.UserListItemResponse;
import com.workflow.demo.domain.entity.Role;
import com.workflow.demo.domain.entity.User;
import com.workflow.demo.repository.RoleRepository;
import com.workflow.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public List<UserListItemResponse> listUsers(String role, Boolean activo) {
        List<User> users = activo == null
            ? userRepository.findAll()
            : userRepository.findByActivo(activo);

        Map<ObjectId, String> roleNameById = roleRepository.findAll().stream()
            .collect(Collectors.toMap(Role::getId, Role::getNombre, (left, right) -> left));

        return users.stream()
            .filter(user -> matchesRole(user, role, roleNameById))
            .map(UserListItemResponse::from)
            .toList();
    }

    private boolean matchesRole(User user, String requestedRole, Map<ObjectId, String> roleNameById) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return true;
        }

        String roleName = roleNameById.get(user.getRoleId());
        if (roleName == null) {
            return false;
        }

        return roleName.equalsIgnoreCase(requestedRole.trim());
    }
}
