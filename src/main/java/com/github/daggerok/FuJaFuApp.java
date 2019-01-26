package com.github.daggerok;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.r2dbc.function.DatabaseClient;
import org.springframework.fu.jafu.ConfigurationDsl;
import org.springframework.fu.jafu.JafuApplication;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static org.springframework.fu.jafu.Jafu.webApplication;
import static org.springframework.fu.jafu.r2dbc.H2R2dbcDsl.r2dbcH2;
import static org.springframework.fu.jafu.web.WebFluxServerDsl.server;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.web.reactive.function.server.ServerResponse.accepted;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
class User {

  private String login;
  private String firstName;
  private String lastName;
}

@RequiredArgsConstructor
class UserRepository {

  private final DatabaseClient client;

  Mono<Long> count() {
    return client.execute()
                 .sql("SELECT COUNT(*) FROM users")
                 .map((row, rowMetadata) -> row.get("COUNT(*)"))
                 .one()
                 .cast(Long.class)
        ;
  }

  Flux<User> findAll() {
    return client.select()
                 .from("users")
                 .as(User.class)
                 .fetch()
                 .all();
  }

  /**
   * https://docs.spring.io/spring-data/r2dbc/docs/1.0.0.M1/reference/html/#r2dbc.datbaseclient.binding
   */
  Mono<User> /* Mono<Map<String, Object>> */ findOne(String login) {
    return client.execute()
                 ////1:
                 //.sql("SELECT * FROM users WHERE login = ?1")
                 //.bind("$1", login)
                 ////2:
                 //.sql("SELECT * FROM users WHERE login = ?1")
                 //.bind(0, login)
                 ////3:
                 .sql("SELECT * FROM users WHERE login = $1")
                 .bind("$1", login)
                 ////4:
                 //.sql("SELECT * FROM users WHERE login = $1")
                 //.bind(0, login)
                 .as(User.class)
                 .fetch()
                 .one()
        ;
  }

  Mono<Void> deleteAll() {
    return client.execute()
                 .sql("DELETE FROM users")
                 .fetch()
                 .one()
                 .then();
  }

  Mono<User> save(User user) {
    return client.insert()
                 .into(User.class)
                 .table("users")
                 .using(user)
                 .map((r, m) -> User.of(r.get("login", String.class),
                                        r.get("first_name", String.class),
                                        r.get("last_name", String.class)))
                 .one();
  }

  void init() {
    client.execute()
          .sql("      CREATE TABLE IF NOT EXISTS users (  " +
                   "    login VARCHAR PRIMARY KEY,        " +
                   "    first_name VARCHAR,               " +
                   "    last_name VARCHAR                 " +
                   "  );                                  "
          )
          .then()
          .then(deleteAll())
          .then(save(User.of("smaldini", "Stéphane", "Maldini")))
          .then(save(User.of("sdeleuze", "Sébastien", "Deleuze")))
          .then(save(User.of("jlong", "Joshua", "Long")))
          .then(save(User.of("bclozel", "Brian", "Clozel")))
          .block();
  }
}

@RequiredArgsConstructor
class UserHandler {

  private final UserRepository repository;

  Mono<ServerResponse> count(ServerRequest request) {
    return ok().contentType(APPLICATION_JSON_UTF8)
               .body(repository.count(), Long.class)
        //.body(repository.count(), new ParameterizedTypeReference<Map<String, Object>>() {})
        ;
  }

  Mono<ServerResponse> all(ServerRequest request) {
    return ok().contentType(APPLICATION_JSON_UTF8)
               .body(repository.findAll(), User.class);
  }

  Mono<ServerResponse> one(ServerRequest request) {
    String login = request.pathVariable("login");
    return ok().contentType(APPLICATION_JSON_UTF8)
               .body(repository.findOne(login), User.class);
    //.body(repository.findOne(login), new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  Mono<ServerResponse> rm(ServerRequest request) {
    return accepted().contentType(APPLICATION_JSON_UTF8)
                     .body(repository.deleteAll(), Void.class);
  }

  Mono<ServerResponse> add(ServerRequest request) {
    return accepted().contentType(APPLICATION_JSON_UTF8)
                     .body(request.bodyToMono(User.class)
                                  .flatMap(repository::save), User.class);
  }
}

public class FuJaFuApp {

  public static void main(String[] args) {

    Consumer<ConfigurationDsl> r2dbcConfig = c -> c
        .beans(beans -> beans.bean(UserRepository.class))
        .enable(r2dbcH2());

    Consumer<ConfigurationDsl> webFluxConfig = c -> c
        .beans(beans -> beans.bean(UserHandler.class))
        .enable(
            server(
                server -> server
                    .router(router -> {
                      UserHandler userHandler = c.ref(UserHandler.class);
                      router.GET("/count", userHandler::count);
                    })
                    .router(router -> {
                      UserHandler userHandler = c.ref(UserHandler.class);
                      router.GET("/{login}", userHandler::one);
                    })
                    .router(router -> {
                      UserHandler userHandler = c.ref(UserHandler.class);
                      router.GET("/", userHandler::all);
                    })
                    .router(router -> {
                      UserHandler userHandler = c.ref(UserHandler.class);
                      router.DELETE("/", userHandler::rm);
                    })
                    .router(router -> {
                      UserHandler userHandler = c.ref(UserHandler.class);
                      router.POST("/", userHandler::add);
                    })
                    .codecs(codecs -> codecs.string()
                                            .jackson())
            )
        );

    JafuApplication jafu = webApplication(
        app -> app.enable(r2dbcConfig)
                  .enable(webFluxConfig)
                  .listener(ApplicationReadyEvent.class, event -> app.ref(UserRepository.class)
                                                                     .init())
    );

    jafu.run(args);
  }
}
