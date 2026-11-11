package com.rbdip.bookstore.review;

import com.rbdip.bookstore.contracts.PurchaseHistory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Отзывы обращаются к истории покупок только через межмодульный контракт. */
@Service
public class ReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final PurchaseHistory purchaseHistory;

    public ReviewService(ReviewRepository reviewRepository, PurchaseHistory purchaseHistory) {
        this.reviewRepository = reviewRepository;
        this.purchaseHistory = purchaseHistory;
    }

    public Review addReview(Long productId, String authorName, Integer rating, String comment) {
        boolean verifiedPurchase = purchaseHistory.hasPurchased(productId, authorName);
        // Информационная проверка: исходный API разрешает отзывы и без покупки.
        LOGGER.debug("Review for product {}: purchase found={}", productId, verifiedPurchase);
        Review review = new Review(productId, authorName == null ? "anonymous" : authorName, rating, comment);
        return reviewRepository.save(review);
    }

    public List<Review> listReviews(Long productId) {
        return reviewRepository.findByProductId(productId);
    }
}
