package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterfaceSwing;
import bguspl.set.UserInterfaceDecorator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.lang.model.util.ElementScanner6;
import javax.naming.spi.DirStateFactory.Result;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        //start all the players threads
        Collections.shuffle(deck);
        placeCardsOnTable();
        for(int i=0; i<env.config.players; i++){
            Thread playerthread = new Thread(players[i], "Player" + i);
            playerthread.start();
        }
        
        //Should we use try and catch and where?
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable(); // Expected an empty board, places 12 cards on the table
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        removeAllCardsFromTable();
        announceWinners();
        terminate();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            // test set?
            if(!table.claimedSetsPlayers.isEmpty()){
                int playerToCheck = table.claimedSetsPlayers.remove();
                int[] setToCheck = table.playerSetToCheck(playerToCheck);
                boolean validSet = true;
                for(int i=0; i<setToCheck.length; i++){
                    if (table.cardToSlot[setToCheck[i]]  == null){
                        validSet = false;
                    }
                }
                if(validSet && env.util.testSet(setToCheck)){
                    updateTimerDisplay(true);
                    players[playerToCheck].point();
                    removeCardsFromTable(setToCheck);
                }
                else{
                    players[playerToCheck].penalty();
                }
            }
             synchronized(table){
                table.notifyAll();
             }
           
            updateTimerDisplay(false);
            placeCardsOnTable(); // replaces the removed set with 3 new cards.
        }
        synchronized(table){
            table.notifyAll();
         }
    }
    

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (Player player : players) {
            player.terminate();
            player.getPlayerThread().interrupt();
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[] cards) {
        for(int i=0; i<cards.length; i++){
            if (table.cardToSlot[cards[i]]!=null){
                table.removeCard(table.cardToSlot[cards[i]]);
            }
                
        }
        //remove all tokens from removed cards.
    }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        for(int i=0; i<env.config.tableSize ; i++){
            if(table.slotToCard[i]==null && deck.size()!=0){
                int cardToPlace = deck.remove(deck.size()-1);
                table.placeCard(cardToPlace, i);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try{
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ex){};
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis; //env.config.turnTimeoutMillis
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), false);
        }
        else if(reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis){
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), true);
        }
        else{
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for(int i=0; i<env.config.tableSize ; i++){
            if (table.slotToCard[i]!=null){
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
                env.ui.removeCard(i);
            }
            
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    protected int[] announceWinners() {
        int highestScore = 0;
        int winnersCount = 0;
        //int winnersCount = 0;
        for(int i = 0; i<players.length; i++){
            if(players[i].score() > highestScore){
                highestScore = players[i].score();
                winnersCount = 1;
            }
            else if(players[i].score() == highestScore){
                winnersCount ++;
            }
        }

        int[] winners = new int[winnersCount];
        int j = 0;
        for(int i=0; i<env.config.players;i++){
            if(players[i].score() == highestScore){
                winners[j] = players[i].id;
                j++;
            }
        }
        env.ui.announceWinner(winners);
        return winners;
    }

    public List<Integer> getDeck(){
        return deck;
    }
}
