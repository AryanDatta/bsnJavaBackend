package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.StoreItem;
import com.bsn.backend.social.model.StoreOrder;
import com.bsn.backend.social.model.WalletEntry;
import com.bsn.backend.social.service.StoreService;
import com.bsn.backend.social.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · wallet & store", description = "money buys nothing here; effort buys everything (§6.9)")
@RestController
@CrossOrigin
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EconomyController {

    private final WalletService walletService;
    private final StoreService storeService;

    public record OrderRequest(String itemId) {
    }

    /* ── wallet ───────────────────────────────────────────── */

    @Operation(summary = "wallet — balance, available, maturing points")
    @GetMapping("/wallet")
    public Map<String, Object> wallet() {
        return walletService.wallet(SecurityUtil.currentUserId());
    }

    @Operation(summary = "ledger statement (append-only)")
    @GetMapping("/wallet/ledger")
    public List<WalletEntry> ledger(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return walletService.statement(SecurityUtil.currentUserId(), page, size);
    }

    /* ── store ────────────────────────────────────────────── */

    @Operation(summary = "store items — aisle=REWARD|COSMETIC, gates included")
    @GetMapping("/store/items")
    public List<StoreItem> items(@RequestParam(required = false) String aisle) {
        return storeService.list(aisle);
    }

    @Operation(summary = "place order — spends points; rewards require KYC, cosmetics may require rank")
    @PostMapping("/store/orders")
    public ResponseEntity<StoreOrder> order(@RequestBody OrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeService.order(SecurityUtil.currentUserId(), req.itemId()));
    }

    @Operation(summary = "my orders")
    @GetMapping("/store/orders")
    public List<StoreOrder> orders(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return storeService.myOrders(SecurityUtil.currentUserId(), page, size);
    }

    @Operation(summary = "order detail + fulfillment state")
    @GetMapping("/store/orders/{id}")
    public StoreOrder order(@PathVariable String id) {
        return storeService.myOrder(SecurityUtil.currentUserId(), id);
    }
}
