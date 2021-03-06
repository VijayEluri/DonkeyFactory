/**
 * Jin - a chess client for internet chess servers.
 * More information is available at http://www.jinchess.com/.
 * Copyright (C) 2003 Alexander Maryanovsky.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package free.jin.scripter;

import free.jin.event.*;
import java.io.*;
import java.util.*;
import free.jin.*;
import free.chess.*;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import free.jin.plugin.Plugin;
import free.jin.plugin.PluginContext;
import free.jin.plugin.PreferencesPanel;
import free.util.MemoryFile;
import free.util.Utilities;
import free.util.swing.UrlDisplayingAction;
import bsh.EvalError;


/**
 * A plugin allowing to run user specified commands or code in response to
 * various server events.
 */

public class Scripter extends Plugin{



  /**
   * A hashtable mapping event type names to <code>ScriptDispatcher</code>
   * instances supporting those types of events.
   */

  private Hashtable dispatchers;



  /**
   * Our menu.
   */

  private JMenu menu;




  /**
   * The constructor. duh.
   */

  public Scripter(){
    dispatchers = new Hashtable();

    registerScriptDispatcher("Connection", new ConnectionScriptDispatcher());
    registerScriptDispatcher("Text (Unparsed text)", new PlainTextScriptDispatcher());
    registerScriptDispatcher("Game", new GameScriptDispatcher());
    registerScriptDispatcher("Seek", new SeekScriptDispatcher());
    registerScriptDispatcher("Friends", new FriendsScriptDispatcher());
    registerScriptDispatcher("User Invoked", new UserInvokedScriptDispatcher());

    menu = new JMenu("Scripter");
  }



  /**
   * Registers the specified <code>ScriptDispatcher</code> to handle the
   * specified event type. If there is already a <code>ScriptDispatcher</code>
   * handling this event, it is removed and any Scripts registered with it will
   * no longer be called. This method, therefore, should only be called before
   * any scripts are actually registered.
   */

  protected void registerScriptDispatcher(String eventType, ScriptDispatcher dispatcher){
    dispatchers.put(eventType, dispatcher);
  }




  /**
   * Returns a <code>ScriptDispatcher</code> for the specified event type or
   * <code>null</code> if the specified event type is not supported.
   */
  
  private ScriptDispatcher getScriptDispatcher(String eventType){
    ScriptDispatcher dispatcher = (ScriptDispatcher)dispatchers.get(eventType);
    if ((dispatcher == null) || !dispatcher.isSupportedBy(getConnection()))
      return null;

    return dispatcher;
  }




  /**
   * Returns an array of supported event types.
   */

  public String [] getSupportedEventTypes(){
    JinConnection conn = getConnection();

    Enumeration eventTypesEnum = dispatchers.keys();
    Vector eventTypesVector = new Vector(dispatchers.size());

    while (eventTypesEnum.hasMoreElements()){
      String eventType = (String)eventTypesEnum.nextElement();
      ScriptDispatcher dispatcher = (ScriptDispatcher)dispatchers.get(eventType);
      if (dispatcher.isSupportedBy(conn))
        eventTypesVector.addElement(eventType);
    }

    String [] eventTypesArr = new String[eventTypesVector.size()];
    eventTypesVector.copyInto(eventTypesArr);

    return eventTypesArr;
  }




  /**
   * Returns a list of the subtypes of the specified event type or
   * <code>null</code> if it has no subtypes.
   */

  public String [] getEventSubtypes(String eventType){
    ScriptDispatcher dispatcher = getScriptDispatcher(eventType);
    if (dispatcher == null)
      throw new IllegalArgumentException("The specified event type ("+eventType+") is not supported");

    return dispatcher.getEventSubtypes();
  }



  /**
   * Returns a list of the available variables and sample values for the
   * specified event type and a list of event subtypes. Note that some variables
   * may be missing from the list.
   */

  public Object [][] getAvailableVariables(String eventType, String [] eventSubtypes){
    ScriptDispatcher dispatcher = getScriptDispatcher(eventType);
    if (dispatcher == null)
      throw new IllegalArgumentException("The specified event type ("+eventType+") is not supported");

    return dispatcher.getAvailableVars(eventSubtypes);
  }





  /**
   * Gets things going :-)
   */

  public void start(){
    JMenuItem help = new JMenuItem("Help", 'H');
    help.addActionListener(new UrlDisplayingAction(getPluginContext().getMainFrame(),
      "http://www.jinchess.com/docs/scripter/"));

    menu.add(help);
    menu.addSeparator();

    loadScripts();
  }




  /**
   * Calls <code>saveScripts</code>.
   */

  public void saveState(){
    saveScripts();
  }



  /**
   * Returns the menu for this plugin.
   */
  
  public JMenu createPluginMenu(){
    return menu;
  }





  /**
   * Adds the specified <code>Script</code> to the list of registered scripts.
   *
   * @throws IllegalArgumentException if the specified script's event type is
   * not supported.
   */

  public void addScript(Script script){
    if (script == null)
      throw new IllegalArgumentException("Null script specified");

    String eventType = script.getEventType();
    ScriptDispatcher dispatcher = getScriptDispatcher(eventType);
    if (dispatcher == null)
      throw new IllegalArgumentException(""+script+" is of an unsupported/unknown event type ("+eventType+")");

    if (dispatcher instanceof UserInvokedScriptDispatcher){
      menu.add(new UserInvokedScriptMenuItem(script, getPluginContext().getMainFrame()));
    }

    dispatcher.addScript(script);
  }



  /**
   * Removes the specified <code>Script</code> from the list of registered
   * scripts.
   */

  public void removeScript(Script script){
    String eventType = script.getEventType();
    ScriptDispatcher dispatcher = getScriptDispatcher(eventType);
    if (dispatcher == null)
      throw new IllegalArgumentException(""+script+" is of an unsupported/unknown event type ("+eventType+")");

    if (dispatcher instanceof UserInvokedScriptDispatcher){
      int count = menu.getMenuComponentCount();
      for (int i = 0; i < count; i++){
        Component comp = menu.getMenuComponent(i);
        if (comp instanceof UserInvokedScriptMenuItem){
          UserInvokedScriptMenuItem item = (UserInvokedScriptMenuItem)comp;
          if (item.getScript() == script){
            menu.remove(item);
            break;
          }
        }
      }
    }

    dispatcher.removeScript(script);
  }




  /**
   * Returns an array containing all the currently registered
   * <code>Scripts</code>.
   */

  public Script [] getScripts(){
    Vector scriptsVector = new Vector();
    String [] eventTypes = getSupportedEventTypes();
    
    for (int i = 0; i < eventTypes.length; i++){
      String eventType = eventTypes[i];
      ScriptDispatcher dispatcher = getScriptDispatcher(eventType);
      Script [] scripts = dispatcher.getScripts();
      for (int j = 0; j < scripts.length; j++)
        scriptsVector.addElement(scripts[j]);
    }

    Script [] scriptsArr = new Script[scriptsVector.size()];
    scriptsVector.copyInto(scriptsArr);

    return scriptsArr;
  } 




  /**
   * Returns <code>true</code> to indicate that we have a preferences UI.
   */

  public boolean hasPreferencesUI(){
    return true;
  }



  /**
   * Returns the Scripter's preferences UI panel.
   */

  public PreferencesPanel getPreferencesUI(){
    return new ScripterPreferencesPanel(this);
  }



  /**
   * Loads all the scripts from user files.
   */

  private void loadScripts(){
    int scriptCount = getIntegerProperty("scripts.count", 0);

    for (int i = 0; i < scriptCount; i++){
      MemoryFile scriptFile = getFile("script-"+i);
      if (scriptFile == null)
        continue;
      Script script = parseScript(scriptFile.getInputStream());

      try{
        if (script != null)
          addScript(script);
      } catch (IllegalArgumentException e){
          System.err.println("WARNING: "+e.getMessage()+". Script "+script+" will not be run");
          continue;
        }
    }
  }




  /**
   * Saves all the scripts into user files.
   */

  private void saveScripts(){
    Script [] scripts = getScripts();

    setIntegerProperty("scripts.count", scripts.length);

    for (int i = 0; i < scripts.length; i++){
      Script script = scripts[i];
      MemoryFile scriptFile = createFile("script-"+i);
      try{
        OutputStream out = scriptFile.getOutputStream();
        writeScript(script, out);
        out.close();
      } catch (IOException e){
          e.printStackTrace();
        }
    }
  }




  /**
   * Parses and returns a <code>Script</code>.
   */

  private Script parseScript(InputStream in){
    try{
      DataInputStream dataIn = new DataInputStream(in);
      Properties props = new Properties();

      // Not using the Properties.load() way because it's not meant 
      // for saving code and can probably break it.
      int propsCount = dataIn.readInt();
      for (int i = 0; i < propsCount; i++){
        String propName = dataIn.readUTF();
        String propValue = dataIn.readUTF();

        props.put(propName, propValue);
      }

      String scriptName = props.getProperty("name");
      String scriptType = props.getProperty("type");
      String eventType = props.getProperty("event-type");
      boolean enabled = "true".equals(props.getProperty("enabled"));
      String eventSubtypesCount = props.getProperty("event-subtype.count");
      String [] eventSubtypes = null;
      if (eventSubtypesCount != null){
        eventSubtypes = new String[Integer.parseInt(eventSubtypesCount)];
        for (int i = 0; i < eventSubtypes.length; i++)
          eventSubtypes[i] = props.getProperty("event-subtype."+i);
      }

      PluginContext context = getPluginContext();

      Script script;
      if ("beanshell".equals(scriptType)){
        String code = props.getProperty("code");
        script = new BeanShellScript(context, scriptName, eventType, eventSubtypes, code);
      }
      else if ("commands".equals(scriptType)){
        String condition = props.getProperty("condition");
        int commandCount = Integer.parseInt(props.getProperty("command-count"));
        String [] commands = new String[commandCount];
        for (int i = 0; i < commandCount; i++)
          commands[i] = props.getProperty("command-"+i);
        script = new CommandScript(context, scriptName, eventType, eventSubtypes, condition, commands);
      }
      else
        return null;
      script.setEnabled(enabled);

      return script;
    } catch (IOException e){
        e.printStackTrace();
        return null;
      }
      catch (EvalError e){
        e.printStackTrace();
        return null;
      }
  }




  /**
   * Writes the specified <code>Script</code> into the specified
   * <code>OutputStream</code>.
   */

  private void writeScript(Script script, OutputStream out) throws IOException{
    String scriptType = script.getType();

    Properties props = new Properties();
    props.put("name", script.getName());
    props.put("type", scriptType);
    props.put("event-type", script.getEventType());
    props.put("enabled", script.isEnabled() ? "true" : "false");

    String [] eventSubtypes = script.getEventSubtypes();
    if (eventSubtypes != null){
      props.put("event-subtype.count", String.valueOf(eventSubtypes.length));
      for (int i = 0; i < eventSubtypes.length; i++)
        props.put("event-subtype."+i, eventSubtypes[i]);
    }

    if ("beanshell".equals(scriptType)){
      BeanShellScript bshScript = (BeanShellScript)script;
      String code = bshScript.getCode();
      props.put("code", code);
    }
    else if ("commands".equals(scriptType)){
      CommandScript cmdScript = (CommandScript)script;
      String condition = cmdScript.getCondition();
      String [] commands = cmdScript.getCommands();

      props.put("condition", condition);
      props.put("command-count", String.valueOf(commands.length));

      for (int i = 0; i < commands.length; i++)
        props.put("command-"+i, commands[i]);
    }

    DataOutputStream dataOut = new DataOutputStream(out);
    dataOut.writeInt(props.size());

    Enumeration propNames = props.propertyNames();
    while (propNames.hasMoreElements()){
      String propName = (String)propNames.nextElement();
      String propValue = props.getProperty(propName);
      dataOut.writeUTF(propName);
      dataOut.writeUTF(propValue);
    }
  }




  /**
   * An abstract base class for classes responsible for supporting scripting for
   * a certain event type. It allows registering and unregistering scripts and
   * testing whether the event type is supported by a specified
   * <code>JinConnection</code> implementation.
   */ 

  protected abstract class ScriptDispatcher{


    /**
     * The list of user defined scripts we're running when the supported event
     * occurs.
     */

    private Vector scripts = new Vector();



    /**
     * An empty array.
     */

    private String [] EMPTY_ARRAY = new String[0];



    /**
     * The constructor. If we don't do this, jikes declares the constructor with
     * default access for some reason.
     */

    public ScriptDispatcher(){

    }



    /**
     * Returns a list of event subtypes supported by this ScriptDispatcher.
     * The returned array may be empty if the supported event has no subtypes.
     */

    public String [] getEventSubtypes(){
      String [] subtypes = getEventSubtypesImpl();
      return subtypes == null ? null : (String [])(subtypes.clone());
    }



    /**
     * Returns an array holding the names of supported event subtypes or
     * <code>null</code> if there are no subtypes.
     * Implementations may return the actual array (without copying it) as the
     * externally visible method, <code>getEventSubtypes</code> copies the
     * returned array by itself.
     */

    protected abstract String [] getEventSubtypesImpl();



    /**
     * Adds the specified script to the list of scripts that are run when the
     * supported event occurs.
     */

    public void addScript(Script script){
      if (scripts.size() == 0)
        registerForEvent(getConnection().getJinListenerManager());

      scripts.addElement(script);
    }



    /**
     * Removes the specified script from the list of scripts that are run when
     * the supported event occurs.
     */

    public void removeScript(Script script){
      if (!scripts.removeElement(script))
        throw new IllegalArgumentException("The specified script ("+script+") has not been previously registered with this ScriptDispatcher ("+this+").");

      if (scripts.size() == 0)
        unregisterForEvent(getConnection().getJinListenerManager());
    }




    /**
     * Returns the scripts registered with this <code>ScriptDispatcher</code>.
     */

    public Script [] getScripts(){
      Script [] scriptsArr = new Script[scripts.size()];
      scripts.copyInto(scriptsArr);

      return scriptsArr;
    }




    /**
     * Runs all the scripts.
     */

    protected void runScripts(JinEvent evt, String eventSubtype, Object [][] vars){
      String [] supportedSubtypes = getEventSubtypesImpl();
      if ((supportedSubtypes != null) && ((eventSubtype == null) ||
                                         !Utilities.isElementOf(supportedSubtypes, eventSubtype))){
        System.err.println("Unknown event subtype occurred: "+eventSubtype);
        return;
      }

      if (vars == null)
        vars = new Object[0][];

      int scriptCount = scripts.size();
      for (int i = 0; i < scriptCount; i++){
        Script script = (Script)scripts.elementAt(i);
        String [] eventSubtypes = script.getEventSubtypes();
        if (script.isEnabled() && ((eventSubtype == null) || Utilities.isElementOf(eventSubtypes, eventSubtype))){
          try{
            script.run(evt, eventSubtype, vars);
          } catch (RuntimeException e){
              e.printStackTrace();
            }
        }
      }
    }




    /**
     * Returns <code>true</code> if the specified <code>JinConnection</code>
     * supports the event type this <code>EventTypeSupport</code> is for.
     * Returns <code>false</code> otherwise.
     */

    public abstract boolean isSupportedBy(JinConnection conn);
    

    /**
     * Registers for the event with the specified
     * <code>JinListenerManager</code>.
     */

    protected abstract void registerForEvent(JinListenerManager listenerManager);


    /**
     * Unregisters for the event with the specified
     * <code>JinListenerManager</code>.
     */

    protected abstract void unregisterForEvent(JinListenerManager listenerManager);



    /**
     * Returns a list of the variables provided for scripts by this
     * ScriptDispatcher for the specified event subtypes with sample values of
     * the variables. There is no necessity to provide a complete list, but the
     * more frequently used variables should be included. Variables for which it
     * is impossible or hard to provide sample values should not be included.
     */

    protected abstract Object [][] getAvailableVars(String [] eventSubtypes);

  }



  /**
   * A <code>ScriptDispatcher</code> for <code>ConnectionEvents</code>.
   */

  private class ConnectionScriptDispatcher extends ScriptDispatcher implements ConnectionListener{

    private String [] subtypes = new String[]{"Connect", "Login", "Disconnect"};
    protected String [] getEventSubtypesImpl(){return subtypes;}

    public boolean isSupportedBy(JinConnection conn){return true;}

    public void registerForEvent(JinListenerManager listenerManager){
      listenerManager.addConnectionListener(this);      
    }

    public void unregisterForEvent(JinListenerManager listenerManager){
      listenerManager.removeConnectionListener(this);
    }

    public void connectionEstablished(ConnectionEvent evt){runScripts(evt, subtypes[0], null);}
    public void connectionLoggedIn(ConnectionEvent evt){runScripts(evt, subtypes[1], null);}
    public void connectionLost(ConnectionEvent evt){runScripts(evt, subtypes[2], null);}

    protected Object [][] getAvailableVars(String [] eventSubtypes){
      return null;
    }
  }




  /**
   * A <code>ScriptDispatcher</code> for <code>PlainTextEvents</code>.
   */

  private class PlainTextScriptDispatcher extends ScriptDispatcher implements PlainTextListener{

    protected String [] getEventSubtypesImpl(){return null;}

    public boolean isSupportedBy(JinConnection conn){return true;}

    public void registerForEvent(JinListenerManager listenerManager){
      listenerManager.addPlainTextListener(this);      
    }

    public void unregisterForEvent(JinListenerManager listenerManager){
      listenerManager.removePlainTextListener(this);
    }

    public void plainTextReceived(PlainTextEvent evt){
      runScripts(evt, null, new Object[][]{{"text", evt.getText()}});
    }

    protected Object [][] getAvailableVars(String [] eventSubtypes){
      return new Object[][]{{"text", "hello!"}};
    }

  }




  
  /**
   * A <code>ScriptDispatcher</code> for <code>GameEvents</code>.
   */

  private class GameScriptDispatcher extends ScriptDispatcher implements GameListener{

    private String [] subtypes = new String[]{"Game Start", "Move", "Takeback/Backward", "Board Flip", "Illegal Move Attempt", "Clock Update", "Other Position Change", "Game End"};
    protected String [] getEventSubtypesImpl(){return subtypes;}


    public boolean isSupportedBy(JinConnection conn){return true;}

    public void registerForEvent(JinListenerManager listenerManager){
      listenerManager.addGameListener(this);      
    }

    public void unregisterForEvent(JinListenerManager listenerManager){
      listenerManager.removeGameListener(this);
    }

    /**
     * Creates the basic set of variables for the specified GameEvent.
     */

    private Vector createVarsVector(GameEvent evt){
      Vector vars = new Vector(25);

      Game game = evt.getGame();

      vars.addElement(new Object[]{"game", game});

      int gameType = game.getGameType();
      String gameTypeString;
      switch (gameType){
        case Game.MY_GAME: gameTypeString = "my"; break;
        case Game.OBSERVED_GAME: gameTypeString = "observed"; break;
        case Game.ISOLATED_BOARD: gameTypeString = "isolated"; break;
        default:
          throw new IllegalStateException("Unknown game type: "+gameType);
      }

      vars.addElement(new Object[]{"gameType", gameTypeString});
      vars.addElement(new Object[]{"initialPosition", game.getInitialPosition()});
      vars.addElement(new Object[]{"variant", game.getVariant().getName()});

      Player userPlayer = game.getUserPlayer();
      vars.addElement(new Object[]{"whiteName", game.getWhiteName()});
      vars.addElement(new Object[]{"blackName", game.getBlackName()});
      vars.addElement(new Object[]{"whiteTime", new Integer(game.getWhiteTime()/(1000*60))});
      vars.addElement(new Object[]{"whiteInc", new Integer(game.getWhiteInc()/1000)});
      vars.addElement(new Object[]{"blackTime", new Integer(game.getBlackTime()/(1000*60))});
      vars.addElement(new Object[]{"blackInc", new Integer(game.getBlackInc()/1000)});
      vars.addElement(new Object[]{"whiteRating", new Integer(game.getWhiteRating())});
      vars.addElement(new Object[]{"blackRating", new Integer(game.getBlackRating())});
      vars.addElement(new Object[]{"whiteTitle", game.getWhiteTitles()});
      vars.addElement(new Object[]{"blackTitle", game.getBlackTitles()});

      if (userPlayer != null){
        if (userPlayer.isWhite()){
          vars.addElement(new Object[]{"myName", game.getWhiteName()});
          vars.addElement(new Object[]{"oppName", game.getBlackName()});
          vars.addElement(new Object[]{"myTime", new Integer(game.getWhiteTime()/(1000*60))});
          vars.addElement(new Object[]{"myInc", new Integer(game.getWhiteInc()/1000)});
          vars.addElement(new Object[]{"oppTime", new Integer(game.getBlackTime()/(1000*60))});
          vars.addElement(new Object[]{"oppInc", new Integer(game.getBlackInc()/1000)});
          vars.addElement(new Object[]{"myRating", new Integer(game.getWhiteRating())});
          vars.addElement(new Object[]{"oppRating", new Integer(game.getBlackRating())});
          vars.addElement(new Object[]{"myTitle", game.getWhiteTitles()});
          vars.addElement(new Object[]{"oppTitle", game.getBlackTitles()});
        }
        else{
          vars.addElement(new Object[]{"oppName", game.getWhiteName()});
          vars.addElement(new Object[]{"myName", game.getBlackName()});
          vars.addElement(new Object[]{"oppTime", new Integer(game.getWhiteTime()/(1000*60))});
          vars.addElement(new Object[]{"oppInc", new Integer(game.getWhiteInc()/1000)});
          vars.addElement(new Object[]{"myTime", new Integer(game.getBlackTime()/(1000*60))});
          vars.addElement(new Object[]{"myInc", new Integer(game.getBlackInc()/1000)});
          vars.addElement(new Object[]{"oppRating", new Integer(game.getWhiteRating())});
          vars.addElement(new Object[]{"myRating", new Integer(game.getBlackRating())});
          vars.addElement(new Object[]{"oppTitle", game.getWhiteTitles()});
          vars.addElement(new Object[]{"myTitle", game.getBlackTitles()});
        }
        vars.addElement(new Object[]{"userPlayer", game.getUserPlayer().toString().toLowerCase()});
      }

      vars.addElement(new Object[]{"isGameRated", new Boolean(game.isRated())});
      vars.addElement(new Object[]{"ratingCategory", game.getRatingCategoryString()});
      vars.addElement(new Object[]{"isPlayed", new Boolean(game.isPlayed())});
      vars.addElement(new Object[]{"isTimeOdds", new Boolean(game.isTimeOdds())});

      return vars;
    }

    public void gameStarted(GameStartEvent evt){
      Vector varsVector = createVarsVector(evt);
      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[0], vars);
    }

    public void moveMade(MoveMadeEvent evt){
      Vector varsVector = createVarsVector(evt);
      varsVector.addElement(new Object[]{"move", evt.getMove()});
//      varsVector.addElement(new Object[]{"isNewMove", new Boolean(evt.isNew())});
      // Since I'm not sure myself whether isNewMove does what it was intended to,
      // let's not confuse the user...

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[1], vars);
    }

    public void positionChanged(PositionChangedEvent evt){
      Vector varsVector = createVarsVector(evt);
      varsVector.addElement(new Object[]{"newPosition", evt.getPosition()});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[6], vars);
    }

    public void takebackOccurred(TakebackEvent evt){
      Vector varsVector = createVarsVector(evt);
      varsVector.addElement(new Object[]{"takebackCount", new Integer(evt.getTakebackCount())});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[2], vars);
    }

    public void illegalMoveAttempted(IllegalMoveEvent evt){
      Vector varsVector = createVarsVector(evt);
      varsVector.addElement(new Object[]{"illegalMove", evt.getMove()});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[4], vars);
    }

    public void clockAdjusted(ClockAdjustmentEvent evt){
      Vector varsVector = createVarsVector(evt);
      varsVector.addElement(new Object[]{"player", evt.getPlayer().toString().toLowerCase()});
      varsVector.addElement(new Object[]{"time", new Integer(evt.getTime())});
      varsVector.addElement(new Object[]{"isClockRunning", new Boolean(evt.isClockRunning())});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[5], vars);
    }

    public void boardFlipped(BoardFlipEvent evt){
      Vector varsVector = createVarsVector(evt);
      varsVector.addElement(new Object[]{"isFlipped", new Boolean(evt.isFlipped())});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[3], vars);
    }

    public void gameEnded(GameEndEvent evt){
      Vector varsVector = createVarsVector(evt);

      int gameResult = evt.getResult();
      Player userPlayer = evt.getGame().getUserPlayer();
      String gameResultString;
      if (userPlayer == null){
        switch (gameResult){
          case Game.WHITE_WINS: gameResultString = "1-0"; break;
          case Game.BLACK_WINS: gameResultString = "0-1"; break;
          case Game.DRAW: gameResultString = "1/2-1/2"; break;
          case Game.UNKNOWN_RESULT: gameResultString = "unknown"; break;
          case Game.GAME_IN_PROGRESS:
          default:
            throw new IllegalStateException("Unknown/bad game result value: "+gameResult);
        }
      }
      else if (userPlayer.isWhite()){
        switch (gameResult){
          case Game.WHITE_WINS: gameResultString = "win"; break;
          case Game.BLACK_WINS: gameResultString = "loss"; break;
          case Game.DRAW: gameResultString = "draw"; break;
          case Game.UNKNOWN_RESULT: gameResultString = "unknown"; break;
          case Game.GAME_IN_PROGRESS:
          default:
            throw new IllegalStateException("Unknown game result value: "+gameResult);
        }
      }
      else{ // isBlack()
        switch (gameResult){
          case Game.WHITE_WINS: gameResultString = "loss"; break;
          case Game.BLACK_WINS: gameResultString = "win"; break;
          case Game.DRAW: gameResultString = "draw"; break;
          case Game.UNKNOWN_RESULT: gameResultString = "unknown"; break;
          case Game.GAME_IN_PROGRESS:
          default:
            throw new IllegalStateException("Unknown game result value: "+gameResult);
        }
      }

      varsVector.addElement(new Object[]{"gameResult", gameResultString});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[7], vars);
    }

    protected Object [][] getAvailableVars(String [] eventSubtypes){
      Vector varsVector = new Vector(25);
      Game game = new Game(Game.MY_GAME, new Position(), 0, "AlexTheGreat", "Kasparov", 5*60*1000, 2000,
        5*60*1000, 2000, 1800, 2852, "blah", "Blitz", true, true, "C", "GM", false, Player.WHITE_PLAYER);

      varsVector.addElement(new Object[]{"game", game});
      varsVector.addElement(new Object[]{"gameType", new Integer(game.getGameType())});
      varsVector.addElement(new Object[]{"initialPosition", game.getInitialPosition()});
      varsVector.addElement(new Object[]{"variant", game.getVariant()});

      varsVector.addElement(new Object[]{"myName", game.getWhiteName()});
      varsVector.addElement(new Object[]{"oppName", game.getBlackName()});
      varsVector.addElement(new Object[]{"myTime", new Integer(game.getWhiteTime()/(1000*60))});
      varsVector.addElement(new Object[]{"myInc", new Integer(game.getWhiteInc()/1000)});
      varsVector.addElement(new Object[]{"oppTime", new Integer(game.getBlackTime()/(1000*60))});
      varsVector.addElement(new Object[]{"oppInc", new Integer(game.getBlackInc()/1000)});
      varsVector.addElement(new Object[]{"myRating", new Integer(game.getWhiteRating())});
      varsVector.addElement(new Object[]{"oppRating", new Integer(game.getBlackRating())});
      varsVector.addElement(new Object[]{"myTitle", game.getWhiteTitles()});
      varsVector.addElement(new Object[]{"oppTitle", game.getBlackTitles()});

      varsVector.addElement(new Object[]{"whiteName", game.getWhiteName()});
      varsVector.addElement(new Object[]{"blackName", game.getBlackName()});
      varsVector.addElement(new Object[]{"whiteTime", new Integer(game.getWhiteTime()/(1000*60))});
      varsVector.addElement(new Object[]{"whiteInc", new Integer(game.getWhiteInc()/1000)});
      varsVector.addElement(new Object[]{"blackTime", new Integer(game.getBlackTime()/(1000*60))});
      varsVector.addElement(new Object[]{"blackInc", new Integer(game.getBlackInc()/1000)});
      varsVector.addElement(new Object[]{"whiteRating", new Integer(game.getWhiteRating())});
      varsVector.addElement(new Object[]{"blackRating", new Integer(game.getBlackRating())});
      varsVector.addElement(new Object[]{"whiteTitle", game.getWhiteTitles()});
      varsVector.addElement(new Object[]{"blackTitle", game.getBlackTitles()});

      Move move = new ChessMove(Square.parseSquare("e2"), Square.parseSquare("e4"),
        Player.WHITE_PLAYER, false, false, false, null, null, "e4");

      if (Utilities.isElementOf(eventSubtypes, subtypes[1])){
        varsVector.addElement(new Object[]{"move", move});
        varsVector.addElement(new Object[]{"isNewMove", new Boolean(true)});
      }

      if (Utilities.isElementOf(eventSubtypes, subtypes[6]))
        varsVector.addElement(new Object[]{"newPosition", new Position()});

      if (Utilities.isElementOf(eventSubtypes, subtypes[2]))
        varsVector.addElement(new Object[]{"takebackCount", new Integer(3)});

      if (Utilities.isElementOf(eventSubtypes, subtypes[4]))
        varsVector.addElement(new Object[]{"illegalMove", move});

      if (Utilities.isElementOf(eventSubtypes, subtypes[5])){
        varsVector.addElement(new Object[]{"player", Player.WHITE_PLAYER.toString().toLowerCase()});
        varsVector.addElement(new Object[]{"time", new Integer(4*60*1000)});
        varsVector.addElement(new Object[]{"isClockRunning", new Boolean(true)});
      }

      if (Utilities.isElementOf(eventSubtypes, subtypes[3]))
        varsVector.addElement(new Object[]{"isFlipped", new Boolean(true)});

      if (Utilities.isElementOf(eventSubtypes, subtypes[7]))
        varsVector.addElement(new Object[]{"gameResult", "win"});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);
      return vars;
    }


  }




  /**
   * A <code>ScriptDispatcher</code> for <code>SeekEvents</code>.
   */

  private class SeekScriptDispatcher extends ScriptDispatcher implements SeekListener{

    private String [] subtypes = new String[]{"Post", "Withdraw"};
    protected String [] getEventSubtypesImpl(){return subtypes;}

    public boolean isSupportedBy(JinConnection conn){return (conn instanceof SeekJinConnection);}

    public void registerForEvent(JinListenerManager listenerManager){
      ((SeekJinListenerManager)listenerManager).addSeekListener(this);
    }

    public void unregisterForEvent(JinListenerManager listenerManager){
      ((SeekJinListenerManager)listenerManager).removeSeekListener(this);
    }

    /**
     * Creates the basic set of variables for the specified SeekEvent.
     */

    private Vector createVarsVector(SeekEvent evt){
      Vector vars = new Vector(15);

      Seek seek = evt.getSeek();

      vars.addElement(new Object[]{"seek", seek});
      vars.addElement(new Object[]{"name", seek.getSeekerName()});
      vars.addElement(new Object[]{"title", seek.getSeekerTitle()});
      vars.addElement(new Object[]{"rating", new Integer(seek.getSeekerRating())});
      vars.addElement(new Object[]{"isProvisional", new Boolean(seek.isSeekerProvisional())});
      vars.addElement(new Object[]{"isRegistered", new Boolean(seek.isSeekerRegistered())});
      vars.addElement(new Object[]{"isComputer", new Boolean(seek.isSeekerComputer())});
      vars.addElement(new Object[]{"ratingCategory", seek.getRatingCategoryString()});
      vars.addElement(new Object[]{"time", new Integer(seek.getTime()/(1000*60))});
      vars.addElement(new Object[]{"inc", new Integer(seek.getInc()/1000)});
      vars.addElement(new Object[]{"isRated", new Boolean(seek.isRated())});
      String colorString = seek.getSoughtColor() == null ? null :
                          (seek.getSoughtColor().isWhite() ? "white" : "black");
      vars.addElement(new Object[]{"color", colorString});
      vars.addElement(new Object[]{"ratingLimited", new Boolean(seek.isRatingLimited())});
      vars.addElement(new Object[]{"minRating", new Integer(seek.getMinRating())});
      vars.addElement(new Object[]{"maxRating", new Integer(seek.getMaxRating())});
      vars.addElement(new Object[]{"isManualAccept", new Boolean(seek.isManualAccept())});
      vars.addElement(new Object[]{"isFormula", new Boolean(seek.isFormula())});

      return vars;
    }


    public void seekAdded(SeekEvent evt){
      Vector varsVector = createVarsVector(evt);
      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[0], vars);
    }

    public void seekRemoved(SeekEvent evt){
      Vector varsVector = createVarsVector(evt);
      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);

      runScripts(evt, subtypes[1], vars);
    }


    protected Object [][] getAvailableVars(String [] eventSubtypes){
      Vector varsVector = new Vector(25);
      
      Seek seek = new Seek("64", "AlexTheGreat", "C", 1800, false, true, true, true, Chess.getInstance(),
        "Blitz", 5*60*1000, 2000, true, null, true, 1700, 1900, false, false);

      varsVector.addElement(new Object[]{"seek", seek});
      varsVector.addElement(new Object[]{"name", seek.getSeekerName()});
      varsVector.addElement(new Object[]{"title", seek.getSeekerTitle()});
      varsVector.addElement(new Object[]{"rating", new Integer(seek.getSeekerRating())});
      varsVector.addElement(new Object[]{"isProvisional", new Boolean(seek.isSeekerProvisional())});
      varsVector.addElement(new Object[]{"isRegistered", new Boolean(seek.isSeekerRegistered())});
      varsVector.addElement(new Object[]{"isComputer", new Boolean(seek.isSeekerComputer())});
      varsVector.addElement(new Object[]{"ratingCategory", seek.getRatingCategoryString()});
      varsVector.addElement(new Object[]{"time", new Integer(seek.getTime()/(1000*60))});
      varsVector.addElement(new Object[]{"inc", new Integer(seek.getInc()/1000)});
      varsVector.addElement(new Object[]{"isRated", new Boolean(seek.isRated())});
      String colorString = seek.getSoughtColor() == null ? null :
                          (seek.getSoughtColor().isWhite() ? "white" : "black");
      varsVector.addElement(new Object[]{"color", colorString});
      varsVector.addElement(new Object[]{"ratingLimited", new Boolean(seek.isRatingLimited())});
      varsVector.addElement(new Object[]{"minRating", new Integer(seek.getMinRating())});
      varsVector.addElement(new Object[]{"maxRating", new Integer(seek.getMaxRating())});
      varsVector.addElement(new Object[]{"isManualAccept", new Boolean(seek.isManualAccept())});
      varsVector.addElement(new Object[]{"isFormula", new Boolean(seek.isFormula())});

      Object [][] vars = new Object[varsVector.size()][];
      varsVector.copyInto(vars);
      return vars;
    }

  }




  /**
   * A <code>ScriptDispatcher</code> for <code>FriendsEvents</code>.
   */

  private class FriendsScriptDispatcher extends ScriptDispatcher implements FriendsListener{

    private String [] subtypes = new String[]{"Online", "Connected", "Disconnected", "Added", "Removed"};
    protected String [] getEventSubtypesImpl(){return subtypes;}

    public boolean isSupportedBy(JinConnection conn){return (conn instanceof FriendsJinConnection);}

    public void registerForEvent(JinListenerManager listenerManager){
      ((FriendsJinListenerManager)listenerManager).addFriendsListener(this);
    }

    public void unregisterForEvent(JinListenerManager listenerManager){
      ((FriendsJinListenerManager)listenerManager).removeFriendsListener(this);
    }

    public void friendOnline(FriendsEvent evt){
      runScripts(evt, subtypes[0], new Object[][]{{"name", evt.getFriendName()}});
    }

    public void friendConnected(FriendsEvent evt){
      runScripts(evt, subtypes[1], new Object[][]{{"name", evt.getFriendName()}});
    }

    public void friendDisconnected(FriendsEvent evt){
      runScripts(evt, subtypes[2], new Object[][]{{"name", evt.getFriendName()}});
    }

    public void friendAdded(FriendsEvent evt){
      runScripts(evt, subtypes[3], new Object[][]{{"name", evt.getFriendName()}});
    }

    public void friendRemoved(FriendsEvent evt){
      runScripts(evt, subtypes[4], new Object[][]{{"name", evt.getFriendName()}});
    }

    protected Object [][] getAvailableVars(String [] eventSubtypes){
      return new Object[][]{{"name", "AlexTheGreat"}};
    }


  }




  /**
   * A <code>ScriptDispatcher</code> for scripts that are invoked directly by
   * the user.
   */

  private class UserInvokedScriptDispatcher extends ScriptDispatcher{

    protected String [] getEventSubtypesImpl(){return null;}

    public boolean isSupportedBy(JinConnection conn){return true;}

    public void registerForEvent(JinListenerManager listenerManager){}

    public void unregisterForEvent(JinListenerManager listenerManager){}

    protected Object [][] getAvailableVars(String [] eventSubtypes){
      return null;
    }


  }


}
