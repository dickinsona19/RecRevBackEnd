package com.BossLiftingClub.BossLifting.Webhooks.Wellhub;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wellhub")
public class WellhubController {


    @PostMapping("/notifyofuser")
    public ResponseEntity<String> handleNotifyOfUser(@RequestBody Object payload) {
        System.out.println(payload);
        return ResponseEntity.ok("Payload received and logged");
    }

}
