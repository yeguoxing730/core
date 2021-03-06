package com.ftp.upload;

import com.ftp.util.FTPUtil;
import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: uc203808
 * Date: 4/29/16
 * Time: 6:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class FTPUploadDirectoryTest {
    public static void main(String[] args) {
        String server = "10.35.63.22";
        int port = 21;
        String user = "anonymous";
        String pass = "";

        FTPClient ftpClient = new FTPClient();

        try {
            // connect and login to the server
            ftpClient.connect(server, port);
            ftpClient.login(user, pass);

            // use local passive mode to pass firewall
            ftpClient.enterLocalPassiveMode();

            System.out.println("Connected");

            String remoteDirPath = "guoxing/java/ftp";
            String localDirPath = "C:\\IDEA\\ideaWorkspaces\\cma\\gddp";

            FTPUtil.uploadDirectory(ftpClient, remoteDirPath, localDirPath, "");

            // log out and disconnect from the server
            ftpClient.logout();
            ftpClient.disconnect();

            System.out.println("Disconnected");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
