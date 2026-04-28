package com.workflow.demo.workflowdesign.controller;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.workflow.demo.domain.entity.Department;
import com.workflow.demo.repository.DepartmentRepository;
import com.workflow.demo.workflowdesign.dto.CreateDepartmentRequest;
import com.workflow.demo.workflowdesign.dto.DepartmentResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    public List<DepartmentResponse> listar() {
        return departmentRepository.findAll()
            .stream()
            .map(DepartmentResponse::from)
            .toList();
    }

    @PostMapping
    public DepartmentResponse crear(@Valid @RequestBody CreateDepartmentRequest request) {
        Department department = new Department();
        department.setNombre(request.getNombre().trim());
        department.setDescripcion(request.getDescripcion());
        department.setActivo(request.getActivo() != null ? request.getActivo() : true);

        try {
            Department saved = departmentRepository.save(department);
            return DepartmentResponse.from(saved);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ya existe un departamento con nombre " + request.getNombre());
        }
    }
}