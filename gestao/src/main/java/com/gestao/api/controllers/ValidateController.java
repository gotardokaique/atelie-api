package com.gestao.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.gen.core.api.AbstractController;
import com.gen.core.api.MethodMapping;

@RestController
@RequestMapping("/api/v1/validate")
public class ValidateController extends AbstractController {

    @MethodMapping(path = "/me", type = RequestMethod.GET)
    public ResponseEntity<Void> getMe() {
        return ResponseEntity.noContent().build();
    }
}
