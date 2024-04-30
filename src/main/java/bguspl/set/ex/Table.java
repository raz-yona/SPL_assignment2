package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    protected volatile List<Integer> [] tokensOfPlayers; //An array of lists that keeps track of tokens on the table for each players.


    Object lock=new Object();

    BlockingQueue<Integer> claimedSetsPlayers = new LinkedBlockingQueue<Integer>() ;



    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        tokensOfPlayers = new LinkedList[env.config.players];
        for(int i=0; i<env.config.players; i++){
            tokensOfPlayers[i] = new LinkedList<Integer>();
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(Integer slot) {
        if(slot!=null){
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}
    
            cardToSlot[slotToCard[slot]] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
            for(int i=0; i<tokensOfPlayers.length;i++){
                if(tokensOfPlayers[i]!=null && tokensOfPlayers[i].contains(slot)){
                    removeToken(i, slot);
                }
            }
        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if(slotToCard[slot] != null)
        {
            if(tokensOfPlayers[player].size()<3){
                tokensOfPlayers[player].add(slot);
                env.ui.placeToken(player, slot);
                //Inserts the player to the queue of claimed sets
                if(tokensOfPlayers[player].size()==3){
                    try{
                        claimedSetsPlayers.put(player);
                    }catch(InterruptedException ex){}
                    synchronized(this){
                        try{
                            wait();
                        }catch(InterruptedException ex){}
                    }
                }
            }
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
            public boolean removeToken(int player, Integer slot) {
        if(tokensOfPlayers[player]!=null && tokensOfPlayers[player].contains(slot)){ 
            env.ui.removeToken(player, slot);
            tokensOfPlayers[player].remove(slot);
            return true;
        }
        return false;
    }

    public int[] playerSetToCheck(int player){
        int[] ans = new int[env.config.featureSize];
        for(int i=0; i<tokensOfPlayers[player].size(); i++){
            int slot = tokensOfPlayers[player].get(i);
            ans[i] = slotToCard[slot];
        }
        return ans;
    }
}