/*
 * Copyright  2017
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.example.pcc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Reference;
import org.springframework.data.gemfire.config.annotation.EnableEntityDefinedRegions;
import org.springframework.data.gemfire.config.annotation.EnableIndexing;
import org.springframework.data.gemfire.config.annotation.EnablePdx;
import org.springframework.data.gemfire.mapping.MappingPdxSerializer;
import org.springframework.data.gemfire.mapping.annotation.Indexed;
import org.springframework.data.gemfire.mapping.annotation.Region;
import org.springframework.data.repository.CrudRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

@Log
@EnableIndexing
@EnableEntityDefinedRegions
@EnablePdx(serializerBeanName = "customPdxSerializer")
@EnableGemFireHttpSession(poolName = "DEFAULT", regionName = "Sessions")
@SpringBootApplication
public class GemfireApplication {

  private ApplicationRunner titledRunner(String title, ApplicationRunner rr) {
    return args -> {
      log.info(title.toUpperCase() + ":");
      rr.run(args);
    };
  }

  // skipped geo repository. Need to come back to it.
  @Bean
  ApplicationRunner gemfireRepositories(OrderRepository orderRepository, LineItemRepository lineItemRepository) {

    return titledRunner("gemfireRepositories", args -> {

      Long orderId = generateId();

      List<LineItem> itemList = Arrays.asList(new LineItem(orderId, generateId(), "plunger"),
              new LineItem(orderId, generateId(), "soup"), new LineItem(orderId, generateId(), "coffee mug"));
      itemList.stream().map(lineItemRepository::save).forEach(li -> log.info(li.toString()));

      Order order = new Order(orderId, new Date(), itemList);
      orderRepository.save(order);

      Collection<Order> found = orderRepository.findByWhen(order.getWhen());
      found.forEach(o -> log.info("found: " + o.toString()));
    });
  }

  // skipped pubsub redis example

  @Bean
  ApplicationRunner cache(OrderService orderService) {
    return titledRunner("caching", a -> {
      Runnable measure = () -> orderService.byId(1L);
      log.info("first " + measure(measure));
      log.info("two " + measure(measure));
      log.info("three " + measure(measure));
    });
  }

  private Long generateId() {
    long tmp = new Random().nextLong();
    return Math.max(tmp, tmp * -1);
  }

  private long measure(Runnable r) {
    long start = System.currentTimeMillis();
    r.run();
    long stop = System.currentTimeMillis();
    return stop - start;
  }

  @Bean
  MappingPdxSerializer customPdxSerializer() {

    MappingPdxSerializer pdxSerializer = new MappingPdxSerializer();

    pdxSerializer.setIncludeTypeFilters(type -> type != null && ShoppingCart.class.isAssignableFrom(type) && Order.class.isAssignableFrom(type) && LineItem.class.isAssignableFrom(type));

    return pdxSerializer;
  }

  public static void main(String[] args) {
    SpringApplication.run(GemfireApplication.class, args);
  }

}

@Service
class OrderService {

  @Cacheable("Order")
  public Order byId(Long id) {
    // @formatter:off
    try {
      Thread.sleep(1000 * 10);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
    // @formatter:on
    return new Order(id, new Date(), Collections.emptyList());
  }
}

interface OrderRepository extends CrudRepository<Order, Long> {
  Collection<Order> findByWhen(Date d);
}

interface LineItemRepository extends CrudRepository<LineItem, Long> {
}

@AllArgsConstructor
@NoArgsConstructor
@Region("Order")
@Data
class Order implements Serializable {

  @Id
  private Long id;

  @Indexed
  private Date when;

  @Reference
  private List<LineItem> lineItems;
}

@AllArgsConstructor
@NoArgsConstructor
@Region("LineItem")
@Data
class LineItem implements Serializable {

  @Indexed
  private Long orderId;

  @Id
  private Long id;

  private String description;
}

@NoArgsConstructor
class ShoppingCart implements Serializable {

  private final Collection<Order> orders = new ArrayList<>();

  public void addOrder(Order order) {
    this.orders.add(order);
  }

  public Collection<Order> getOrders() {
    return this.orders;
  }
}


@Log
@Controller
@SessionAttributes("cart")
class CartSessionController {

  private final AtomicLong ids = new AtomicLong();

  @ModelAttribute("cart")
  ShoppingCart cart() {
    log.info("creating new cart");
    return new ShoppingCart();
  }

  @GetMapping("/orders")
  String orders(@ModelAttribute("cart") ShoppingCart cart,
                Model model) {
    cart.addOrder(new Order(ids.incrementAndGet(), new Date(), Collections.emptyList()));
    model.addAttribute("orders", cart.getOrders());
    return "orders";
  }
}
