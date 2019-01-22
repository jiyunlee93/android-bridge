package com.example.ingji.myapplication;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

import static android.os.SystemClock.sleep;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    UsbSerialDriver driver;
    UsbDeviceConnection connection;
    UsbSerialPort port;
    String receivedText = "";
    int port_baudRate = 9600;
    int port_dataBit = 8;
    boolean portStatus = false;
    boolean udpStatus = false;
    boolean tcpServerStatus = false;
    boolean tcpClientStatus = false;
    Handler handler = new Handler();
    //TCP
    int port_task;
    String ip_task;
    ServerSocket serverSocket_task;
    Socket socket_task;
    Socket socket_received;
    int checkServerClient;

    ByteArrayOutputStream byteArrayOutputStream;
    InputStream in;
    PrintWriter writer;
    PrintWriter writer_received;
    int tcpCross = -1;
    // for UDP
    DatagramSocket socket;
    //UI
    EditText edtSendLocalPort, edtSendTargetIP, edtSendTargetPort, edtSendBaudRate;
    EditText edtReceiveLocalPort, edtReceiveTargetIP, edtReceiveTargetPort, edtReceiveBaudRate;
    Button btnSendConnect, btnReceiveConnect, btnBridgeConnect;
    String sendProtocol, receivedProtocol;
    boolean sendConnectCheck, receivedConnectCheck, bridgeOpen, check_TCPreceived;

    //check Thread Start
    boolean usbThread = false;
    boolean tcpServerThread = false;
    boolean tcpClientThread = false;
    boolean udpThread = false;
    int socketClose = -1;


    boolean errorCheck=false;
    String errorString="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_udp);
        //initialization
        receivedText = "";

        //initialization UI value
        btnSendConnect = findViewById(R.id.btn_SendConnect);
        btnReceiveConnect = findViewById(R.id.btn_ReceiveConnect);
        btnBridgeConnect = findViewById(R.id.btn_BridgeConnect);
//
        edtSendLocalPort = findViewById(R.id.edt_SendlocalPort);
        edtSendTargetIP = findViewById(R.id.edt_SendTargetIP);
        edtSendTargetPort = findViewById(R.id.edt_SendTargetPort);
        edtSendBaudRate = findViewById(R.id.edt_SendBaudRate);
        edtReceiveLocalPort = findViewById(R.id.edt_ReceivelocalPort);
        edtReceiveTargetIP = findViewById(R.id.edt_ReceiveTargetIP);
        edtReceiveTargetPort = findViewById(R.id.edt_ReceiveTargetPort);
        edtReceiveBaudRate = findViewById(R.id.edt_ReceiveBaudRate);
        bridgeOpen = false;
        check_TCPreceived = false;
        btnSendConnect.setOnClickListener(buttonSendConnectOnClickListener);
        btnReceiveConnect.setOnClickListener(buttonReceiveConnectOnClickListener);
        btnBridgeConnect.setOnClickListener(buttonBridgeConnectOnClickListener);
    }

    View.OnClickListener buttonBridgeConnectOnClickListener = new View.OnClickListener() {
        public void onClick(View arg0) {
            //bridge connect btn event handler
            if (bridgeOpen) {
                bridgeOpen = false;
                btnBridgeConnect.setText(R.string.bridgeConnect);
            } else {
                if (sendConnectCheck && receivedConnectCheck) {
                    //둘다 연결 되었다면
                    bridgeOpen = true;
                    btnBridgeConnect.setText(R.string.bridgeDisconnect);
                } else {
                    Toast.makeText(getApplicationContext(), "BRIDGE open ERROR : ", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    View.OnClickListener buttonSendConnectOnClickListener = new View.OnClickListener() {
        public void onClick(View arg0) {
            switch (sendProtocol) {
                case "tcpServer":
                    if (btnSendConnect.getText().toString().equals("DISCONNECT")) {
                        //이미 연결이 되어 있는 경우 연결 끊기
                        socketClose = 1;
                        sendConnectCheck = false;
                        btnSendConnect.setText(R.string.connect);
                        if (receivedConnectCheck && receivedProtocol.equals("tcpServer")) {
                            //received 도 tcp 였다면 같이 끊기
                            receivedConnectCheck = false;
                            btnReceiveConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 Protocol이므로 received도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (bridgeOpen) {
                            //bridge 연결 상태였다면 bridge close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        btnSendConnect.setText(R.string.disconnect);
                        sendConnectCheck = true;
                        if (receivedConnectCheck && receivedProtocol.equals("tcpServer")) {
                            //이미 연결이 되어 있는 경우 PASS
                            break;
                        }
                        //PORT 연결
                        int tcp_port = Integer.parseInt(edtSendLocalPort.getText().toString());
                        String ip = "";
                        checkServerClient = 0;
                        tcpThread(ip, tcp_port);
                        tcpServerThread = true;
                    }
                    break;
                case "tcpClient":
                    if (btnSendConnect.getText().toString().equals("DISCONNECT")) {
                        //이미 연결이 되어 있는 경우 연결 끊기
                        socketClose = 2;
                        sendConnectCheck = false;
                        btnSendConnect.setText(R.string.connect);
                        if (receivedConnectCheck && receivedProtocol.equals("tcpClient")) {
                            //received 도 tcp였다면 같이 끊기
                            receivedConnectCheck = false;
                            btnReceiveConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 protocol로 received도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (bridgeOpen) {
                            //bridge 연결 상태였다면 bridge close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        btnSendConnect.setText(R.string.disconnect);
                        sendConnectCheck = true;
                        if (receivedConnectCheck && receivedProtocol.equals("tcpClient")) {
                            //이미 연결이 되어 있는 경우 PASS
                            break;
                        }
                        //PORT 연결
                        int tcp_port = Integer.parseInt(edtSendTargetPort.getText().toString());
                        String ip = edtSendTargetIP.getText().toString();
                        checkServerClient = 1;
                        tcpThread(ip, tcp_port);
                        tcpClientThread = true;
                    }
                    break;
                case "udp":
                    if (btnSendConnect.getText().toString().equals("DISCONNECT")) {
                        //이미 연결이 되어 있는 경우 연결 끊기
                        socketClose(0);
                        sendConnectCheck = false;
                        btnSendConnect.setText(R.string.connect);
                        if (receivedConnectCheck && receivedProtocol.equals("udp")) {
                            //received 도 usb였다면 같이 끊기
                            receivedConnectCheck = false;
                            btnReceiveConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 protocol로 received도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (bridgeOpen) {
                            //bridge 연결 상태였다면 bridge close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        btnSendConnect.setText(R.string.disconnect);
                        sendConnectCheck = true;
                        if (receivedConnectCheck && receivedProtocol.equals("udp")) {
                            //이미 연결이 되어 있는 경우 PASS
                            break;
                        }
                        //PORT 연결
                        int port = Integer.parseInt(edtSendLocalPort.getText().toString());
                        if (udpOpen(port)) {
                            if (!udpThread) {
                                //Thread 실행 한 적 없으면 thread 실행
                                udpThread();
                                udpThread = true;
                            }
                        } else {
                            //port연결 에러 시 원상태로
                            btnSendConnect.setText(R.string.connect);
                            sendConnectCheck = false;
                        }
                    }
                    break;
                case "usbToSerial":
                    if (btnSendConnect.getText().toString().equals("DISCONNECT")) {
                        //이미 연결이 되어 있는 경우 연결 끊기
                        portClose();
                        sendConnectCheck = false;
                        btnSendConnect.setText(R.string.connect);
                        if (receivedConnectCheck && receivedProtocol.equals("usbToSerial")) {
                            //received 도 usb였다면 같이 끊기
                            receivedConnectCheck = false;
                            btnReceiveConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 protocol로 received도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (bridgeOpen) {
                            //bridge 연결 상태였다면 bridge close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        btnSendConnect.setText(R.string.disconnect);
                        sendConnectCheck = true;
                        if (receivedConnectCheck && receivedProtocol.equals("usbToSerial")) {
                            //이미 연결이 되어 있는 경우 PASS
                            break;
                        }
                        //PORT 연결
                        if (portOpen()) {
                            if (!usbThread) {
                                //Thread 실행 한 적 없으면 thread 실행
                                usbToSerialThread();
                                usbThread = true;
                            }
                        } else {
                            //port연결 에러 시 원상태로
                            btnSendConnect.setText(R.string.connect);
                            sendConnectCheck = false;
                        }
                    }
                    break;

            }
        }
    };
    View.OnClickListener buttonReceiveConnectOnClickListener = new View.OnClickListener() {
        public void onClick(View arg0) {
            switch (receivedProtocol) {
                //연결 중복되지 않도록 예외처리
                case "tcpServer":
                    if (btnReceiveConnect.getText().toString().equals("DISCONNECT")) {
                        //DISCONNECT 코드
                        socketClose = 1;
                        receivedConnectCheck = false;
                        btnReceiveConnect.setText(R.string.connect);
                        if (sendConnectCheck && sendProtocol.equals("tcpServer")) {
                            //SEND도 같은 USB일 경우 같이 DISCONNECT
                            sendConnectCheck = false;
                            btnSendConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 protocol로 Send도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (sendProtocol.equals("tcpClient")) {
                            tcpCross = -1;
                        }
                        if (bridgeOpen) {
                            //BRIDGE Open 일 경우 close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        receivedConnectCheck = true;
                        btnReceiveConnect.setText(R.string.disconnect);
                        if (sendConnectCheck && sendProtocol.equals("tcpServer")) {
                            //SEND가 이미 연결한 상태라면 PASS
                            break;
                        } else {
                            if (sendProtocol.equals("tcpClient")) {
                                tcpCross = 0;
                            }
                            int tcp_port = Integer.parseInt(edtReceiveLocalPort.getText().toString());
                            String ip = "";
                            checkServerClient = 2;
                            tcpThread(ip, tcp_port);
                            tcpServerThread = true;
                        }
                    }
                    break;
                case "tcpClient":
                    if (btnReceiveConnect.getText().toString().equals("DISCONNECT")) {
                        //DISCONNECT 코드
                        socketClose = 2;
                        receivedConnectCheck = false;
                        btnReceiveConnect.setText(R.string.connect);
                        if (sendConnectCheck && sendProtocol.equals("tcpClient")) {
                            //SEND도 같은 USB일 경우 같이 DISCONNECT
                            sendConnectCheck = false;
                            btnSendConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 protocol로 Send 도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (sendProtocol.equals("tcpServer")) {
                            tcpCross = -1;
                        }
                        if (bridgeOpen) {
                            //BRIDGE Open 일 경우 close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        receivedConnectCheck = true;
                        btnReceiveConnect.setText(R.string.disconnect);
                        if (sendConnectCheck && sendProtocol.equals("tcpClient")) {
                            //SEND가 이미 연결한 상태라면 PASS
                            break;
                        } else {
                            if (sendProtocol.equals("tcpServer")) {
                                tcpCross = 1;
                            }
                            int tcp_port = Integer.parseInt(edtReceiveTargetPort.getText().toString());
                            String ip = edtReceiveTargetIP.getText().toString();
                            checkServerClient = 3;
                            tcpThread(ip, tcp_port);
                            tcpClientThread = true;
                        }
                    }
                    break;
                case "udp":
                    if (btnReceiveConnect.getText().toString().equals("DISCONNECT")) {
                        //DISCONNECT 코드
                        if(udpThread==true)
                            socketClose(0);
                        receivedConnectCheck = false;
                        btnReceiveConnect.setText(R.string.connect);
                        if (sendConnectCheck && sendProtocol.equals("udp")) {
                            //SEND도 같은 USB일 경우 같이 DISCONNECT
                            sendConnectCheck = false;
                            btnSendConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 protocol로 Send 도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (bridgeOpen) {
                            //BRIDGE Open 일 경우 close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        receivedConnectCheck = true;
                        btnReceiveConnect.setText(R.string.disconnect);
                        if (sendConnectCheck && sendProtocol.equals("udp")) {
                            //SEND가 이미 연결한 상태라면 PASS
                            break;
                        }
                    }
                    break;
                case "usbToSerial":
                    if (btnReceiveConnect.getText().toString().equals("DISCONNECT")) {
                        //DISCONNECT 코드
                        portClose();
                        receivedConnectCheck = false;
                        btnReceiveConnect.setText(R.string.connect);
                        if (sendConnectCheck && sendProtocol.equals("usbToSerial")) {
                            //SEND도 같은 USB일 경우 같이 DISCONNECT
                            sendConnectCheck = false;
                            btnSendConnect.setText(R.string.connect);
                            Toast.makeText(getApplicationContext(), "같은 protocol로 Send 도 종료됨", Toast.LENGTH_SHORT).show();
                        }
                        if (bridgeOpen) {
                            //BRIDGE Open 일 경우 close
                            bridgeOpen = false;
                            btnBridgeConnect.setText(R.string.bridgeConnect);
                        }
                    } else {
                        //CONNECT 코드
                        receivedConnectCheck = true;
                        btnReceiveConnect.setText(R.string.disconnect);
                        if (sendConnectCheck && sendProtocol.equals("usbToSerial")) {
                            //SEND가 이미 연결한 상태라면 PASS
                            break;
                        } else {
                            if (portOpen()) {
                                if (!usbThread) {
                                    //Thread 실행 한 적 없으면 thread 실행
                                    usbToSerialThread();
                                    usbThread = true;
                                }
                            } else {
                                //port open 오류시 원위치
                                btnReceiveConnect.setText(R.string.connect);
                                receivedConnectCheck = false;
                            }
                        }
                    }
                    break;
            }
        }
    };
    public void onButtonClick(View v) {
        switch (v.getId()) {
            case R.id.btn_server:
                edtSendLocalPort.setEnabled(true);
                edtSendTargetIP.setEnabled(false);
                edtSendTargetPort.setEnabled(false);
                edtSendBaudRate.setEnabled(false);
                sendProtocol = "tcpServer";
                break;
            case R.id.btn_client:
                edtSendLocalPort.setEnabled(false);
                edtSendTargetIP.setEnabled(true);
                edtSendTargetPort.setEnabled(true);
                edtSendBaudRate.setEnabled(false);
                sendProtocol = "tcpClient";
                break;
            case R.id.btn_udp:
                edtSendLocalPort.setEnabled(true);
                edtSendTargetIP.setEnabled(true);
                edtSendTargetPort.setEnabled(true);
                edtSendBaudRate.setEnabled(false);
                sendProtocol = "udp";
                break;
            case R.id.btn_usbToSerial:
                edtSendLocalPort.setEnabled(false);
                edtSendTargetIP.setEnabled(false);
                edtSendTargetPort.setEnabled(false);
                edtSendBaudRate.setEnabled(true);
                sendProtocol = "usbToSerial";
                break;
            case R.id.btn_serverReceive:
                edtReceiveLocalPort.setEnabled(true);
                edtReceiveTargetIP.setEnabled(false);
                edtReceiveTargetPort.setEnabled(false);
                edtReceiveBaudRate.setEnabled(false);
                receivedProtocol = "tcpServer";
                break;
            case R.id.btn_clientReceive:
                edtReceiveLocalPort.setEnabled(false);
                edtReceiveTargetIP.setEnabled(true);
                edtReceiveTargetPort.setEnabled(true);
                edtReceiveBaudRate.setEnabled(false);
                receivedProtocol = "tcpClient";
                break;
            case R.id.btn_udpReceive:
                edtReceiveLocalPort.setEnabled(true);
                edtReceiveTargetIP.setEnabled(true);
                edtReceiveTargetPort.setEnabled(true);
                edtReceiveBaudRate.setEnabled(false);
                receivedProtocol = "udp";
                break;
            case R.id.btn_usbToSerialReceive:
                edtReceiveLocalPort.setEnabled(false);
                edtReceiveTargetIP.setEnabled(false);
                edtReceiveTargetPort.setEnabled(false);
                edtReceiveBaudRate.setEnabled(true);
                receivedProtocol = "usbToSerial";
                break;
        }
    }

    //UDP protocol
    public void udpThread() {
        Toast.makeText(getApplicationContext(), "UDP FUNCTION START", Toast.LENGTH_SHORT).show();
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    if (udpStatus)
                        udpReceived();
                    handler.post(new Runnable() {
                        public void run() {
                            //Send Code
                            if(errorCheck){
                                errorCheck=false;
                                Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    try {
                        Thread.sleep(5);
                    } catch (Exception e) {
                    }
                }
            }
        });
        t.start();
    }

    public boolean udpOpen(int portNum) {
        try {
            socket = new DatagramSocket(portNum);
            udpStatus = true;
        } catch (SocketException e) {
            errorString=e.toString();
            errorCheck=true;
            return false;
        }
        return true;
    }

    public void udpReceived() {
        try {
            // 데이터를 받을 버퍼
            byte[] inbuf = new byte[256];
            // 데이터를 받을 Packet 생성
            DatagramPacket packet = new DatagramPacket(inbuf, inbuf.length);
            // 데이터 수신 // 데이터가 수신될 때까지 대기됨
            socket.receive(packet);
            receivedText = new String(packet.getData());
            receivedText = receivedText.trim();
            //여기서 받음
            if (bridgeOpen) {
                //bridge 오픈일 때
                switch (receivedProtocol) {
                    case "udp":
                        //UDP로 다시 보낼 때
                        SendData mSendData = new SendData();
                        //보내기 시작
                        mSendData.start();
                        break;
                    case "usbToSerial":
                        String a = receivedText;
                        byte tempBuff[] = a.getBytes();
                        port.write(tempBuff, 5);
                        break;
                    case "tcpServer":
                    case "tcpClient":
                        check_TCPreceived = true;
                        break;
                }
            }
        } catch (IOException e) {
            errorCheck=true;
            errorString=e.toString();
        }
    }

    public void socketClose(int num) {
        try {
            switch (num) {
                case 0:
                    //udp의 경우
                    socket.close();
                    udpStatus = false;
                    break;
                case 1:
                    //tcp Server
                    serverSocket_task.close();
                    if (tcpCross == 0) {
                        socket_received.close();
                    } else {
                        socket.close();
                    }
                    tcpServerStatus = false;
                    break;
                case 2:
                    if (tcpCross == 1) {
                        socket_received.close();
                    } else {
                        socket.close();
                    }
                    tcpClientStatus = false;
                    break;
            }
        } catch (IOException e) {
            //nothing catch
            errorCheck=true;
            errorString=e.toString();
        }
    }

    class SendData extends Thread {
        public void run() {
            try {
                String sIP = edtReceiveTargetIP.getText().toString();
                int sPORT = Integer.parseInt(edtReceiveTargetPort.getText().toString());
                //UDP 통신용 소켓 생성
                DatagramSocket socket = new DatagramSocket();
                //서버 주소 변수
                InetAddress serverAddr = InetAddress.getByName(sIP);
                //보낼 데이터 생성
                String a = receivedText.trim();
                if (!a.equals("")) {
                    byte buf[] = a.getBytes();
                    //패킷으로 변경
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, sPORT);
                    socket.send(packet);
                }

            } catch (Exception e) {
                errorCheck=true;
                errorString=e.toString();
            }
        }
    }

    //USB TO SERIAL
    public boolean portOpen() {
        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Toast.makeText(getApplicationContext(), "availableDrivers is null", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Open a connection to the first available driver.
        driver = availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            Toast.makeText(getApplicationContext(), "connection is null", Toast.LENGTH_SHORT).show();
            //USB에 연결하기 위한 permission 을 유저에게 묻는 문구 보여주기 위해 request Permission()에서 호출
            manager.requestPermission(driver.getDevice(), mPermissionIntent);
            return false;
        }
        // Read some data! Most have just one port (port 0).
        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(port_baudRate, port_dataBit, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "port open && setParameter exception", Toast.LENGTH_SHORT).show();
            return false;
        }
        portStatus = true;
        return true;
    }

    public void usbToSerialThread() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    if (portStatus)
                        receiveMessage();
                    handler.post(new Runnable() {
                        public void run() {
                            if(errorCheck){
                                errorCheck=false;
                                Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    try {
                        Thread.sleep(5);
                    } catch (Exception e) {
                    }
                }
            }
        });
        t.start();
    }

    public void receiveMessage() {
        byte buffer[] = new byte[16];
        try {
            port.read(buffer, 50);
        } catch (IOException er) {
            errorString=er.toString();
            errorCheck=true;
        }
        try {
            receivedText = new String(buffer, "UTF-8");
            receivedText = receivedText.trim();
            if (!receivedText.equals("") && !receivedText.isEmpty()) {
                if (bridgeOpen) {
                    switch (receivedProtocol) {
                        case "usbToSerial":
                            byte tempBuff[] = receivedText.getBytes();
                            port.write(tempBuff, 50);
                            break;
                        case "udp":
                            SendData mSendData = new SendData();
                            //보내기 시작
                            mSendData.start();
                            break;
                        case "tcpServer":
                        case "tcpClient":
                            check_TCPreceived = true;
                            break;
                    }
                }
            }
        } catch (Exception err) {
            errorCheck=true;
            errorString=err.toString();
        }
        sleep(50);
    }

    public boolean portClose() {
        try {
            port.close();
            portStatus = false;

        } catch (IOException e2) {
            Toast.makeText(getApplicationContext(), "port close error", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    public void tcpThread(String ip_, int port_) {
        ip_task = ip_;
        port_task = port_;
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    if (checkServerClient == 0 || checkServerClient == 2) {
                        //Server
                        serverSocket_task = new ServerSocket(port_task);
                        //server 에서는 socket 변수가 clientSocket
                        if (tcpCross == 0) {
                            socket_received = serverSocket_task.accept();
                        } else {
                            socket_task = serverSocket_task.accept();
                        }
                        tcpServerThread = true;
                    } else if (checkServerClient == 1 || checkServerClient == 3) {
                        //Client
                        if (tcpCross == 1) {
                            socket_received = new Socket(ip_task, port_task);
                        } else {
                            socket_task = new Socket(ip_task, port_task);
                        }
                        tcpClientThread = true;
                    }
                    byteArrayOutputStream = new ByteArrayOutputStream(1024);
                    if (tcpCross == 1 || tcpCross == 0) {
                        writer_received = new PrintWriter(socket_received.getOutputStream(), true);
                    } else {
                        in = socket_task.getInputStream();
                        writer = new PrintWriter(socket_task.getOutputStream(), true);
                    }
                } catch (IOException e) {
                    errorCheck=true;
                    errorString=e.toString();
                }
                tcpServerStatus = true;
                while (true) {
                    if (tcpServerStatus || tcpClientStatus && !(socketClose == 1 || socketClose == 2)) {
                        tcpRead();
                    }
                    handler.post(new Runnable() {
                        public void run() {
                            if(errorCheck){
                                Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
                                errorCheck=false;
                            }
                        }
                    });
                }
            }
        });
        t.start();
    }

    public void tcpRead() {
        //initialization
        int bytesRead;
        try {
            //읽을 값이 있을 때만  read 함수 실행
            if ((checkServerClient == 2 || checkServerClient == 3) && (tcpCross == -1)) {
                if (check_TCPreceived && !receivedText.equals("")) {
                    //보낼 내용이 있다면
                    String a = receivedText.trim();
                    writer.println(a);
                    check_TCPreceived = false;
                }
            }else {
                if (in.available() > 0) {
                    byte[] buffer = new byte[1024];
                    bytesRead = in.read(buffer);
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    receivedText = byteArrayOutputStream.toString("UTF-8");
                    byteArrayOutputStream.reset();
                    //Received part
                    if (bridgeOpen) {
                        switch (receivedProtocol) {
                            case "tcpServer":
                            case "tcpClient":
                                if (tcpCross == 0 || tcpCross == 1) {
                                    writer_received.println(receivedText);
                                } else {
                                    writer.println(receivedText);
                                }
                                break;
                            case "udp":
                                SendData mSendData = new SendData();
                                //보내기 시작
                                mSendData.start();
                                break;
                            case "usbToSerial":
                                String a = receivedText;
                                byte tempBuff[] = a.getBytes();
                                port.write(tempBuff, 5);
                                break;
                        }
                    }
                }
            }
            if (!socket_task.isBound()) {
                return;
            }
            sleep(30);
        } catch (IOException e) {
            errorCheck=true;
            errorString=e.toString();
        }
    }
}
