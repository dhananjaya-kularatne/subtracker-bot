package com.subtracker.bot.controller;

import com.subtracker.bot.dto.ApiResponse;
import com.subtracker.bot.dto.CreateSubscriptionRequest;
import com.subtracker.bot.dto.SubscriptionResponse;
import com.subtracker.bot.model.User;
import com.subtracker.bot.service.SubscriptionService;
import com.subtracker.bot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTTP endpoints for managing Subscriptions.
 * the owning user is identified by a telegramChatId query parameter
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserService userService;

    // POST /api/v1/subscriptions?telegramChatId=123456
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @RequestParam Long telegramChatId,
            @Valid @RequestBody CreateSubscriptionRequest request) {

        User user = userService.findOrCreateByTelegramChatId(telegramChatId);
        SubscriptionResponse response = subscriptionService.createSubscription(user, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Subscription created", response));
    }

    // GET /api/v1/subscriptions?telegramChatId=123456
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getSubscriptions(
            @RequestParam Long telegramChatId) {

        User user = userService.findOrCreateByTelegramChatId(telegramChatId);
        List<SubscriptionResponse> responses = subscriptionService.getSubscriptionsForUser(user);

        return ResponseEntity.ok(ApiResponse.success("Subscriptions retrieved", responses));
    }

    // GET /api/v1/subscriptions/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscriptionById(
            @PathVariable Long id) {

        SubscriptionResponse response = subscriptionService.getSubscriptionById(id);
        return ResponseEntity.ok(ApiResponse.success("Subscription retrieved", response));
    }

    // DELETE /api/v1/subscriptions/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSubscription(@PathVariable Long id) {
        subscriptionService.deleteSubscription(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Subscription deleted", null));
    }
}