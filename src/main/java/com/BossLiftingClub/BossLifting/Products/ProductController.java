package com.BossLiftingClub.BossLifting.Products;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Products> getAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping
    public ResponseEntity<Products> addProduct(@Valid @RequestBody Products products) {
        return new ResponseEntity<>(productService.addProduct(products), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Products> updateProduct(@PathVariable Long id, @Valid @RequestBody Products products) {
        return ResponseEntity.ok(productService.updateProduct(id, products));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/purchaseProduct")
    public String purchaseProduct(
            @RequestParam Long productId,
            @RequestParam String stripeCustomerId,
            @RequestParam(defaultValue = "1") int quantity
    ) {
        return productService.createInvoiceForUser(productId, stripeCustomerId, quantity);
    }


    @GetMapping("/by-business-tag/{businessTag}")
    public List<ProductsDTO> getProductsByBusinessTag(@PathVariable String businessTag) {
        List<Products> products = productRepository.findByBusinessTag(businessTag);
        return products.stream()
                .map(ProductsDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // Backward compatibility
    @GetMapping("/by-club-tag/{clubTag}")
    public List<ProductsDTO> getProductsByClubTag(@PathVariable String clubTag) {
        return getProductsByBusinessTag(clubTag);
    }
}
