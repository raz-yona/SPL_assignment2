package bguspl.set.ex;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import javax.sql.RowSet;
import javax.swing.text.TableView.TableRow;

import bguspl.set.Env;
/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private BlockingQueue<Integer> actionQueue = new LinkedBlockingQueue<Integer>(3) ; //thread safe queue   


    protected boolean penaltyFreeze; 
    
    protected boolean pointFreeze; 



    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        penaltyFreeze=false;
        pointFreeze = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if(penaltyFreeze){
                env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
                long timer = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
                while(timer > System.currentTimeMillis()){
                    try{
                        if(timer-System.currentTimeMillis() > 1000){
                            Thread.sleep(900);
                        }
                        else{
                            Thread.sleep(timer-System.currentTimeMillis());
                        }
                    } catch(InterruptedException ex){}
                    env.ui.setFreeze(id, Math.max(0, timer-System.currentTimeMillis()));
                }
                env.ui.setFreeze(id, 0);
                penaltyFreeze = false;
            }
            if(pointFreeze){
                env.ui.setFreeze(id, env.config.pointFreezeMillis);
                try{
                    Thread.sleep(env.config.pointFreezeMillis);
                } catch(InterruptedException ex){}
                env.ui.setFreeze(id, 0);
                pointFreeze = false;
            }
        
            if(!actionQueue.isEmpty()){

               try{
                Integer slot = actionQueue.take();
                if(!table.removeToken(id, slot)){
                    table.placeToken(id, slot);
                }
                }catch(InterruptedException ex){};
                
            }
        

       
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */ 
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName()+"AI" + " starting.");
            while (!terminate) {
                int rand = new Random().nextInt(12);
                keyPressed(rand);
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate=true;
        if(!human){
            aiThread.interrupt();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!pointFreeze && !penaltyFreeze ){

          try{
            actionQueue.put(slot);
          } catch(InterruptedException ex){};
            
        }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        pointFreeze = true;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        penaltyFreeze = true;
    }

    public int score() {
        return score;
    }

    public Thread getPlayerThread(){
        return playerThread;
    }

    public BlockingQueue<Integer> getActionQueue(){
        return actionQueue;
    }
}