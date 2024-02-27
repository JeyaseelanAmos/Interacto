package Common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static DBConnection jdbcConnect;
    private static Connection con;

    private DBConnection() {
        try {
        	Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/Interacto", "vijila", "vijila25");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

     public Connection getConnection() {
        return con;
    }
    public static DBConnection getJdbcConnection() {
        if (jdbcConnect == null) {
            jdbcConnect = new DBConnection(); 
        }
        return jdbcConnect;
    }
}
