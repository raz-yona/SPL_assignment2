package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl   .set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    Table table;
    @Mock
    private Player player;
    @Mock
    private Logger logger;
    private Player[] players;

    void assertInvariants() {
        assertTrue(dealer.getDeck().size() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        table = new Table(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        players = new Player[3];
        players[0] = new Player(env, dealer, table, 0, false);
        players[1] = new Player(env, dealer, table, 1, false);
        players[2] = new Player(env, dealer, table, 2, false);
        dealer = new Dealer(env, table, players);
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

     @Test
    void AnnounceWinners1Winner(){
        boolean isEqual;
        players[0].point();
        int[] expectedWinners = new int[1];
        expectedWinners[0] = players[0].id;
        int[] winners = dealer.announceWinners();
        isEqual = (winners[0] == expectedWinners[0]);
        assertEquals(true, isEqual);
    }

    @Test
    void AnnounceWinners3Winners(){
        boolean isEqual;
        players[0].point();
        players[1].point();
        players[2].point();
        players[0].point();
        players[1].point();
        players[2].point();
        List<Integer> expectedWinnersList = new LinkedList<Integer>();
        expectedWinnersList.add(players[0].id);
        expectedWinnersList.add(players[1].id);
        expectedWinnersList.add(players[2].id);
        int[] winners = dealer.announceWinners();
        isEqual = ((expectedWinnersList.contains(winners[0]))&&(expectedWinnersList.contains(winners[1])) &&((expectedWinnersList.contains(winners[2]))));
        assertEquals(true, isEqual);
    }
}