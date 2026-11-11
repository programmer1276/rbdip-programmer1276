package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rbdip.bookstore.contracts.PurchaseHistory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Модуль отзывов проверяется с подменой контракта, без классов хранения заказов. */
class ReviewServiceTest {

    private final ReviewRepository repository = mock(ReviewRepository.class);
    private final PurchaseHistory history = mock(PurchaseHistory.class);
    private final ReviewService service = new ReviewService(repository, history);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void consultsContractAndStillAllowsReviewWithoutPurchase(boolean purchased) {
        when(history.hasPurchased(1L, "Reader")).thenReturn(purchased);
        when(repository.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
        Review result = service.addReview(1L, "Reader", 5, "Useful");
        verify(history).hasPurchased(1L, "Reader");
        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(repository).save(saved.capture());
        assertThat(result).isSameAs(saved.getValue());
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getAuthorName()).isEqualTo("Reader");
        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getComment()).isEqualTo("Useful");
    }

    @Test
    void keepsAnonymousAuthorDefault() {
        when(repository.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
        assertThat(service.addReview(1L, null, 4, null).getAuthorName()).isEqualTo("anonymous");
        verify(history).hasPurchased(1L, null);
    }

    @Test
    void listsReviewsWithoutQueryingPurchases() {
        List<Review> expected = List.of(new Review(1L, "Reader", 5, "Useful"));
        when(repository.findByProductId(1L)).thenReturn(expected);
        assertThat(service.listReviews(1L)).isSameAs(expected);
        verifyNoInteractions(history);
    }
}
