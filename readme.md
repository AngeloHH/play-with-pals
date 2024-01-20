# Board Games API

This project is a board games API developed in Kotlin using Ktor and Exposed. The API allows playing various board games in real time using WebSockets for communication of moves between players. Authentication in the API is done through JSON Web Tokens (JWT). The main goal of this project was to learn the basics of Kotlin.

#### Notes

- The routes of the API are designed generically to allow the scalability of the project and the easy incorporation of new games. Each game shares the same routes, and the game logic adapts according to the specific configuration of the game.

## List of Games

- [x] Tic Tac Toe
- [ ] Parchis
- [ ] Bingo
- [ ] Connect 4

## Execution Instructions

1.  Clone the repository to your local machine by running the following command in your terminal:
    ```bash
    git clone https://github.com/AngeloHH/play-with-pals.git
    ```
2.  Access the project directory:
    ```bash
    cd play-with-pals
    ```
3. Set up the necessary environment variables for JWT, test credentials, and the database. Edit the `src/main/resources/application.conf` file with the appropriate information. Here is an example configuration:
   ```hocon
   jwt {
       secret = "your_secret"
       issuer = "your_issuer"
       audience = "your_audience"
       realm = "boardgames.com"
   }

   test_credentials {
       username = "SherlockHolmes221B"
       email = "sherlock.holmes221b@bakerstreet.com"
       password = "MoriartyShallNeverWin!"
   }

   database {
       username = "your_db_user"
       password = "your_db_password"
       database = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
       driver = "org.h2.Driver"
   }
   ```
4.  Run the following command to perform the tests:
    ```bash
    ./gradlew test --tests com.boardgames.ApplicationTest
    ```
