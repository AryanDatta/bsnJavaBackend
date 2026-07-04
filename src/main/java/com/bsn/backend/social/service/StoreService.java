package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.common.ForbiddenException;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.StoreItem;
import com.bsn.backend.social.model.StoreOrder;
import com.bsn.backend.social.model.Tier;
import com.bsn.backend.social.model.WalletEntry;
import com.bsn.backend.social.repo.StoreItemRepository;
import com.bsn.backend.social.repo.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The store (§7.2): "MONEY BUYS NOTHING HERE. EFFORT BUYS EVERYTHING."
 * Rewards (real world) require one-time KYC; cosmetics may require rank.
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreItemRepository items;
    private final StoreOrderRepository orders;
    private final ProfileService profileService;
    private final WalletService wallet;
    private final NotificationService notifications;

    public List<StoreItem> list(String aisle) {
        return aisle == null
                ? items.findByStatus("LIVE")
                : items.findByAisleAndStatus(aisle.toUpperCase(), "LIVE");
    }

    public StoreOrder order(String userId, String itemId) {
        StoreItem item = items.findById(itemId)
                .filter(i -> "LIVE".equals(i.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("item not found: " + itemId));
        SocialProfile profile = profileService.byUserId(userId);

        // gates: rank opens the shelf…
        if (item.getMinTier() != null) {
            Tier mine = Tier.valueOf(profile.getRank() == null ? "IRON" : profile.getRank().getTier());
            if (mine.minRr() < Tier.valueOf(item.getMinTier()).minRr()) {
                throw new ForbiddenException(item.getMinTier() + " only — rank opens the shelf");
            }
        }
        // …KYC gates the real world (409 → client runs "Verify to claim · 2 min")
        if (item.isKycRequired() && !"VERIFIED".equals(profile.getKycStatus())) {
            throw new ConflictException("KYC_REQUIRED");
        }
        if (item.getStock() != null && item.getStock() <= 0) {
            throw new ConflictException("out of stock");
        }
        if (wallet.availableBalance(userId) < item.getPricePts()) {
            throw new ConflictException("insufficient available points");
        }

        // spend: effort pays for it (append-only, idempotent per order attempt)
        String orderId = UUID.randomUUID().toString();
        WalletEntry spend = wallet.append(userId, -item.getPricePts(),
                "REWARD".equals(item.getAisle()) ? "SPEND_REWARD" : "SPEND_COSMETIC",
                "store_order", orderId, "order:" + orderId, null);

        boolean cosmetic = "COSMETIC".equals(item.getAisle());
        StoreOrder order = orders.save(StoreOrder.builder()
                .id(orderId).userId(userId)
                .itemId(item.getId()).itemName(item.getName()).aisle(item.getAisle())
                .pricePts(item.getPricePts())
                .state(cosmetic ? "FULFILLED" : "RESERVED")
                .ledgerEntryId(spend == null ? null : spend.getId())
                .fulfillmentType(cosmetic ? "COSMETIC_UNLOCK" : "VOUCHER")
                .fulfillmentDetail(cosmetic ? item.getId() : "fulfillment pending")
                .createdAt(Instant.now())
                .build());

        if (item.getStock() != null) {
            item.setStock(item.getStock() - 1);
            items.save(item);
        }
        notifications.push(userId, "POINTS", null, "store_order", orderId,
                item.getName() + " — " + item.getPricePts() + " pts spent. Earned on camera.");
        return order;
    }

    public List<StoreOrder> myOrders(String userId, int page, int size) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, 50)));
    }

    public StoreOrder myOrder(String userId, String orderId) {
        StoreOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException("not your order");
        }
        return order;
    }
}
