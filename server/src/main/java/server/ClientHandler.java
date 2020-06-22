package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.*;

public class ClientHandler {
    private static Connection connection;
    private static Statement statement;
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private String nick;
    private String login;

    public static void connect() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:chat.db");
        statement = connection.createStatement();
    }

    public static void disconnect(){
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ClientHandler(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //Если в течении 5 секунд не будет сообщений по сокету то вызовится исключение
                    socket.setSoTimeout(3000);

                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/reg ")) {
                            String[] token = str.split(" ");

                            if (token.length < 4) {
                                continue;
                            }

                            boolean succeed = server
                                    .getAuthService()
                                    .registration(token[1], token[2], token[3]);
                            if (succeed) {
                                sendMsg("Регистрация прошла успешно");
                                try {
                                    connect();
                                    System.out.println("БД подключилась");
                                    statement.executeUpdate("INSERT INTO users(login, password, nickname)" + " VALUES ('"+ token[1] + "', '"+ token[2]+ "' , '"+token[3]+ "')");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    disconnect();
                                }
                            } else {
                                sendMsg("Регистрация  не удалась. \n" +
                                        "Возможно логин уже занят, или данные содержат пробел");
                            }
                        }

                        if (str.startsWith("/auth ")) {
                            String[] token = str.split(" ");

                            if (token.length < 3) {
                                continue;
                            }

//                            String newNick = server.getAuthService()
//                                    .getNicknameByLoginAndPassword(token[1], token[2]);

                            String newNick;
                            login = token[1];
                            try {
                              nick = getNick(token);
                            } catch (SQLException e) {
                                e.printStackTrace();
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                            newNick=nick;


                            if (newNick != null) {
                                if (!server.isLoginAuthorized(login)) {
                                    sendMsg("/authok " + newNick);
                                    nick = newNick;
                                    server.subscribe(this);
                                    System.out.println("Клиент: " + nick + " подключился"+ socket.getRemoteSocketAddress());
                                    socket.setSoTimeout(0);
                                    break;
                                } else {
                                    sendMsg("С этим логином уже прошли аутентификацию");
                                }
                            } else {
                                sendMsg("Неверный логин / пароль");
                            }
                        }
                    }

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                sendMsg("/end");
                                break;
                            }
                            if (str.startsWith("/w ")) {
                                String[] token = str.split(" ", 3);

                                if (token.length < 3) {
                                    continue;
                                }

                                server.privateMsg(this, token[1], token[2]);
                            }
                        } else {
                            server.broadcastMsg(nick, str);
                        }
                    }
                }catch (SocketTimeoutException e){
                    sendMsg("/end");
                }
                ///////
                catch (IOException e) {
                    e.printStackTrace();
                }
                 finally {
                    server.unsubscribe(this);
                    System.out.println("Клиент отключился");
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public String getNick() {
//        return nick;
//    }

    public String getLogin() {
        return login;
    }
    public String getNick() {
        return login;
    }

    public String getNick(String [] token) throws SQLException, ClassNotFoundException {
       System.out.println("Мы тут");
       connect();
       String newNick;

       PreparedStatement st = connection.prepareStatement("SELECT nickname FROM users WHERE login=? AND password=?");
       st.setString(1, token[1]);
       st.setString(2, token[2]);


       ResultSet rs = st.executeQuery();

       while (rs.next()){
           String nick = rs.getString("nickname");

           System.out.println(nick);
           return nick;
       }

       rs.close();
       connection.close();

       newNick = nick;
         System.out.println(newNick);
         return newNick;
    }
}
