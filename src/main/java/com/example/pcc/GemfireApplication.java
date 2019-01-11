package com.example.pcc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Reference;
import org.springframework.data.gemfire.config.annotation.EnableEntityDefinedRegions;
import org.springframework.data.gemfire.config.annotation.EnableIndexing;
import org.springframework.data.gemfire.config.annotation.EnablePdx;
import org.springframework.data.gemfire.mapping.annotation.Indexed;
import org.springframework.data.gemfire.mapping.annotation.Region;
import org.springframework.data.repository.CrudRepository;

import java.io.Serializable;
import java.util.*;

@Log
@EnableIndexing
@EnableEntityDefinedRegions
@EnablePdx
@SpringBootApplication
public class GemfireApplication {

  private ApplicationRunner titledRunner(String title, ApplicationRunner rr) {
    return args -> {
      log.info(title.toUpperCase() + ":");
      rr.run(args);
    };
  }

  @Bean
  ApplicationRunner gemfireRepositories(
          OrderRepository orderRepository,
          LineItemRepository lineItemRepository) {

    return titledRunner("gemfireRepositories", args -> {

      Long orderId = generateId();

      List<LineItem> itemList = Arrays.asList(
              new LineItem(orderId, generateId(), "plunger"),
              new LineItem(orderId, generateId(), "soup"),
              new LineItem(orderId, generateId(), "coffee mug"));
      itemList
              .stream()
              .map(lineItemRepository::save)
              .forEach(li -> log.info(li.toString()));

      Order order = new Order(orderId, new Date(), itemList);
      orderRepository.save(order);

      Collection<Order> found = orderRepository.findByWhen(order.getWhen());
      found.forEach(o -> log.info("found: " + o.toString()));
    });
  }

  private Long generateId() {
    long tmp = new Random().nextLong();
    return Math.max(tmp, tmp * -1);
  }

  public static void main(String[] args) {
    SpringApplication.run(GemfireApplication.class, args);
  }

}

interface OrderRepository extends CrudRepository<Order, Long> {
  Collection<Order> findByWhen(Date d);
}

interface LineItemRepository extends CrudRepository<LineItem, Long> {
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Region("Order")
class Order implements Serializable {

  @Id
  private Long id;

  @Indexed
  private Date when;

  @Reference
  private List<LineItem> lineItems;
}

@Region("LineItem")
@Data
@AllArgsConstructor
@NoArgsConstructor
class LineItem implements Serializable {

  @Indexed
  private Long orderId;

  @Id
  private Long id;

  private String description;
}


