package be.snife.sbms.productcomposite.services;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import be.snife.sbms.api.composite.product.*;
import be.snife.sbms.api.core.product.Product;
import be.snife.sbms.api.core.recommendation.Recommendation;
import be.snife.sbms.api.core.review.Review;
import be.snife.sbms.api.exceptions.NotFoundException;
import be.snife.sbms.util.http.ServiceUtil;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class ProductCompositeServiceImpl implements ProductCompositeService {

	private final ServiceUtil serviceUtil;
	private ProductCompositeIntegration integration;

	@Autowired
	public ProductCompositeServiceImpl(ServiceUtil serviceUtil, ProductCompositeIntegration integration) {

		this.serviceUtil = serviceUtil;
		this.integration = integration;
	}

	@Override
	public void createProduct(ProductAggregate body) {

		try {

			log.debug("createCompositeProduct: creates a new composite entity for productId: {}", body.getProductId());

			Product product = new Product(body.getProductId(), body.getName(), body.getWeight(), null);
			integration.createProduct(product);

			if (body.getRecommendations() != null) {
				body.getRecommendations().forEach(r -> {
					Recommendation recommendation = new Recommendation(body.getProductId(), r.getRecommendationId(),
							r.getAuthor(), r.getRate(), r.getContent(), null);
					integration.createRecommendation(recommendation);
				});
			}

			if (body.getReviews() != null) {
				body.getReviews().forEach(r -> {
					Review review = new Review(body.getProductId(), r.getReviewId(), r.getAuthor(), r.getSubject(),
							r.getContent(), null);
					integration.createReview(review);
				});
			}

			log.debug("createCompositeProduct: composite entities created for productId: {}", body.getProductId());

		} catch (RuntimeException re) {
			log.warn("createCompositeProduct failed", re);
			throw re;
		}
	}

	@Override
	public ProductAggregate getProduct(int productId) {
		log.debug("Getting Aggregate Product with productId = {} on {}", productId, serviceUtil.getServiceAddress());

		// Get Product info with productId
		Product product = integration.getProduct(productId);
		log.debug("Getting Product with productId = {}", productId);
		if (product == null) {
			throw new NotFoundException("No product found for productId: " + productId);
		}

		log.debug("Getting Recommendations with productId = {}", productId);
		List<Recommendation> recommendations = integration.getRecommendations(productId);

		log.debug("Getting Reviews with productId = {}", productId);
		List<Review> reviews = integration.getReviews(productId);

		return createProductAggregate(product, recommendations, reviews, serviceUtil.getServiceAddress());
	}

	@Override
	public void deleteProduct(int productId) {

		log.debug("deleteCompositeProduct: Deletes a product aggregate for productId: {}", productId);

		integration.deleteProduct(productId);

		integration.deleteRecommendations(productId);

		integration.deleteReviews(productId);

		log.debug("deleteCompositeProduct: aggregate entities deleted for productId: {}", productId);
	}

	private ProductAggregate createProductAggregate(Product product, List<Recommendation> recommendations,
			List<Review> reviews, String serviceAddress) {

		// 1. Setup product info
		int productId = product.getProductId();
		String name = product.getName();
		int weight = product.getWeight();

		// 2. Copy summary recommendation info, if available
		List<RecommendationSummary> recommendationSummaries = (recommendations == null) ? null
				// iterate over list
				: recommendations.stream()
						.map(r -> new RecommendationSummary(r.getRecommendationId(), r.getAuthor(), r.getRate(),r.getContent()))
						.collect(Collectors.toList());

		// 3. Copy summary review info, if available
		List<ReviewSummary> reviewSummaries = (reviews == null) ? null
				// iterate over list
				: reviews.stream().map(r -> new ReviewSummary(r.getReviewId(), r.getAuthor(), r.getSubject(),r.getContent()))
						.collect(Collectors.toList());

		// 4. Create info regarding the involved microservices addresses
		String productAddress = product.getServiceAddress();
		String reviewAddress = (reviews != null && reviews.size() > 0) ? reviews.get(0).getServiceAddress() : "";
		String recommendationAddress = (recommendations != null && recommendations.size() > 0)
				? recommendations.get(0).getServiceAddress()
				: "";
		ServiceAddresses serviceAddresses = new ServiceAddresses(serviceAddress, productAddress, reviewAddress,
				recommendationAddress);

		return new ProductAggregate(productId, name, weight, recommendationSummaries, reviewSummaries,
				serviceAddresses);
	}
}
