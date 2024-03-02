package com.example.fastcampuskafka.demo3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

@Slf4j
@Controller
public class CheckOutSubmitController {

    private final SaveService saveService;

    public CheckOutSubmitController(SaveService saveService) {
        this.saveService = saveService;
    }

    @PostMapping("/submitCheckOut")
    public String submitCheckOut(CheckOutDto checkOutDto, Model model) {
        log.info(checkOutDto.toString());
        Long checkOutId = saveService.saveCheckOutData(checkOutDto);
        model.addAttribute("checkOutId", checkOutId);
        return "submitComplete";
    }
}
