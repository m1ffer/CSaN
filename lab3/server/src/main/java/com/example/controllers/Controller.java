package com.example.controllers;

import com.example.extension.annotation.HttpMethod;
import org.springframework.web.bind.annotation.*;

@RestController
public class Controller {
    @GetMapping("/")
    public String get(){
        System.out.println("Пришел гет запрос");
        return "You've send get request";
    }
    @PostMapping("/")
    public String post(@RequestBody String body){
        System.out.println("Пришел пост запрос. Тело: " + body);
        return "You've send post request";
    }
    @PutMapping("/")
    public String put(){
        System.out.println("Пришел пут запрос");
        return "You've send put request";
    }
    @DeleteMapping("/")
    public String delete(){
        System.out.println("Пришел делит запрос");
        return "You've send delete request";
    }

    @HttpMethod("COPY")
    @RequestMapping("/")
    public String copy(){
        System.out.println("Пришел копи запрос");
        return "You've send copy request";
    }

    @HttpMethod("MOVE")
    @RequestMapping("/")
    public String move(){
        System.out.println("Пришел мув запрос");
        return "You've send move request";
    }
}
