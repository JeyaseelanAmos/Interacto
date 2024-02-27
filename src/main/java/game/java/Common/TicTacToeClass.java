package Common;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.sql.*;
import java.util.Date;

@ServerEndpoint("/TicTacToeEnd")
public class TicTacToeClass {
	int player1Id;
	int player2Id;
	 private static final Set<Session> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
	    private static final Map<String, Session> usernames = new ConcurrentHashMap<>();
	    private static final String[][] board = {
	            {"", "", ""},
	            {"", "", ""},
	            {"", "", ""}
	    };
	    private static final Stack<Move> moveStack = new Stack<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        updateUserList();
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        removeUser(session);
        broadcast("User removed from web socket");
        updateUserList();
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
    }

    private void updateUserList() {
        Set<String> userNameList = usernames.keySet();
        for (Session session : sessions) {
            StringBuilder userListMessage = new StringBuilder("/users ");
            for (String username : userNameList) {
                if (!usernames.get(username).equals(session)) {
                    userListMessage.append(username).append(" ");
                }
            }

            try {
                session.getBasicRemote().sendText(userListMessage.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    private void broadcast(String message) {
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @OnMessage
    public void onMessage(String message, Session senderSession) {
    	  if (message.startsWith("/gameRequest ")) {
    	        String[] parts = message.split(" ");
    	        if (parts.length == 2) {
    	            String recipient = parts[1].trim();
    	            Session recipientSession = usernames.get(recipient);
    	            if (recipientSession != null) {
    	                try {
    	                    recipientSession.getBasicRemote().sendText("/gameRequest " + getUsername(senderSession));
    	                } catch (IOException e) {
    	                    e.printStackTrace();
    	                }
    	            }    
    	        }     
    	    }
    	  else if (message.startsWith("/name ")) {
              String[] parts = message.split(" ");
              String player1 = parts[1].trim();
              String player2 = parts[2].trim();
  	    	System.out.println(message+"Two players!!! "+player1+"   +    "+player2);
  	    	player1Id = getUserIdByUsername(player1); 
  	    	player2Id = getUserIdByUsername(player2);
              if(player1Id!=-1 && player2Id!=-1) {
              			insertUsers();
              }
          }
    	  else if (message.startsWith("/acceptGame ")) {
    	        String[] parts = message.split(" ");
    	        if (parts.length == 2) {
    	            String requester = parts[1].trim();
    	            Session requesterSession = usernames.get(requester);
    	            if (requesterSession != null) {
    	                try {
    	                    requesterSession.getBasicRemote().sendText("/startGame " + getUsername(senderSession));
    	                    senderSession.getBasicRemote().sendText("/startGame " + requester);
    	                } catch (IOException e) { 
    	                    e.printStackTrace();
    	                }  
    	            }  
    	        }  
    	    }   
    	    else if (message.startsWith("/setusername ")) {
            String username = message.substring(13);
            usernames.put(username, senderSession);
            updateUserList();
        } 
    	    else if (message.startsWith("/undo")) {
                undoMove(senderSession);
            }
    	    else if (message.startsWith("/selected")) {
                String[] parts = message.split(" ");
                if (parts.length == 5) {
                    String x = parts[1].trim();
                    String y = parts[2].trim();
                    String recipient = parts[3].trim();
                    String currentPlayer = parts[4].trim();
                    if (isValidMove(x, y, currentPlayer)) {
                    	int gameId= getGameId(recipient);
                    	storeMoveInDB(Integer.parseInt(x),Integer.parseInt(y), recipient, currentPlayer, gameId);
                        handleMove(Integer.parseInt(x), Integer.parseInt(y), currentPlayer);
                        sendMessage(senderSession, x, y, recipient, currentPlayer);
                        if (checkWin(currentPlayer)) {
                            try {
                                senderSession.getBasicRemote().sendText("/win You win the match");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } 
    }
 
    private boolean isValidMove(String x, String y, String currentPlayer) {
        int row = Integer.parseInt(x);
        int col = Integer.parseInt(y);
        return board[row][col].isEmpty() && currentPlayer.equals("X") || currentPlayer.equals("O");
    }

    private void sendMessage(Session senderSession, String x, String y, String recipient, String currentSimple) {
        Session recipientSession = usernames.get(recipient);
        if (recipientSession != null) {
            try {
                recipientSession.getBasicRemote().sendText("/move " + getUsername(senderSession) + " " + x + " " + y + " " + currentSimple);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getUsername(Session session) {
        for (Map.Entry<String, Session> entry : usernames.entrySet()) {
            if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void removeUser(Session session) {
        String usernameToRemove = null;
        for (Map.Entry<String, Session> entry : usernames.entrySet()) {
            if (entry.getValue().equals(session)) {
                usernameToRemove = entry.getKey();
                break;
            }
        }
        if (usernameToRemove != null) {
            usernames.remove(usernameToRemove);
        }
    }

    private boolean checkWin(String currentPlayer) {
        for (int i = 0; i < 3; i++) {
            if ((board[i][0].equals(currentPlayer) && board[i][1].equals(currentPlayer) && board[i][2].equals(currentPlayer)) ||
                    (board[0][i].equals(currentPlayer) && board[1][i].equals(currentPlayer) && board[2][i].equals(currentPlayer))) {
            	emptyBoard();
            	return true;
            }
        }
        return (board[0][0].equals(currentPlayer) && board[1][1].equals(currentPlayer) && board[2][2].equals(currentPlayer)) ||
                (board[0][2].equals(currentPlayer) && board[1][1].equals(currentPlayer) && board[2][0].equals(currentPlayer));
    }
    private void emptyBoard() {
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[i].length; j++) {
                board[i][j] = "";
            }
        }
    }

    private void handleMove(int row, int col, String player) {
        if (board[row][col].isEmpty()) {
            board[row][col] = player;
            moveStack.push(new Move(row, col, player));
        }
    }

    private void undoMove(Session senderSession) {
        if (!moveStack.isEmpty()) {
            Move lastMove = moveStack.pop();
            int row = lastMove.getRow();
            int col = lastMove.getCol();
            board[row][col] = "";
            String currentPlayer = lastMove.getPlayer();
            broadcastUndo(senderSession, row, col, currentPlayer);
        }
    }
    
    private void broadcastUndo(Session senderSession, int row, int col, String currentPlayer) {
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText("/undo " + row + " " + col + " " + currentPlayer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private static class Move {
        private final int row;
        private final int col;
        private final String player;

        public Move(int row, int col, String player) {
            this.row = row;
            this.col = col;
            this.player = player;
        }

        public int getRow() {
            return row;
        }

        public int getCol() {
            return col;
        }

        public String getPlayer() {
            return player;
        }
    }

	
	public int getUserIdByUsername(String username) {
	    int userID = -1; 
	    try {
	        PreparedStatement preparedStatement = DBConnection.getJdbcConnection().getConnection().prepareStatement("SELECT UserID FROM Users WHERE username = ?");
	        preparedStatement.setString(1, username);
	        ResultSet resultSet = preparedStatement.executeQuery();
	        if (resultSet.next()) {
	            userID = resultSet.getInt("UserID");
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	    return userID;
	}
	public void insertUsers() {
	    try {
	        String insertQuery = "INSERT INTO GameTable (Player1ID, Player2ID, Winner, timeStamp) VALUES (?, ?, ?, ?)";
	        PreparedStatement preparedStatement = DBConnection.getJdbcConnection().getConnection().prepareStatement(insertQuery);
	        preparedStatement.setInt(1, player1Id); 
	        preparedStatement.setInt(2, player2Id);
	        preparedStatement.setInt(3, 1); 
	        java.sql.Timestamp timestamp = new java.sql.Timestamp(new Date().getTime());
	        preparedStatement.setTimestamp(4, timestamp);
	        int rowsAffected = preparedStatement.executeUpdate();
	        System.out.println(rowsAffected+ " Data inserted successfully into GameTable");
	    } catch (SQLException e) {
	        e.printStackTrace();
	    } 
	}
	
	public void storeMoveInDB(int x, int y, String recipient, String XO, int gameId) {
	    try {
	        PreparedStatement preparedStatement = DBConnection.getJdbcConnection().getConnection().prepareStatement(
	            "INSERT INTO MovesTable (GameID, PlayerID, XO, PositionX, PositionY) VALUES (?, ?, ?, ?, ?)"
	        );
	        preparedStatement.setInt(1, gameId);
	        preparedStatement.setInt(2, getUserIdByUsername(recipient));
	        preparedStatement.setString(3, XO);
	        preparedStatement.setInt(4, x);
	        preparedStatement.setInt(5, y);
	        preparedStatement.executeUpdate();
	        System.out.println("sUCCES MOVE");
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}
	
	public int getGameId(String playerName) {
		int gameId=0;
		 try {
		        PreparedStatement preparedStatement = DBConnection.getJdbcConnection().getConnection().prepareStatement("SELECT GameID FROM GameTable JOIN Users ON GameTable.Player1ID = Users.userID OR GameTable.Player2ID = Users.userID WHERE Users.username = ? ORDER BY GameID DESC  LIMIT 1");
		        preparedStatement.setString(1, playerName);
		        ResultSet resultSet = preparedStatement.executeQuery();
		        if (resultSet.next()) {
		             gameId = resultSet.getInt(1);
		        }
		    } catch (SQLException e) {
		        e.printStackTrace();
		    }
		 return gameId;
	}
}