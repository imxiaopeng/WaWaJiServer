package com.zego.zegowawaji_server.device;

import android.content.Context;

import com.zego.base.utils.AppLogger;

import java.io.File;
import java.io.IOException;


/**
 * <p>Copyright © 2017 Zego. All rights reserved.</p>
 *
 * @author realuei on 07/11/2017.
 */

/*
接口通信协议

1.通信配置 （USAR1：12V RX1 TX1 GND）
通信接口：RS232 串口
波特率：9600
数据位：8 位
停止位：1 位
校验位：0 位

2.数据帧格式 （# data1 data2... *）
名称	定义	备注
HEAD(帧头)	0X23	#
DAT1(数据 1)	Data[0]
DAT2(数据 2)	Data[1]
END(帧尾)	0X2a	*

3.数据定义
 开始游戏初始化 每次游戏开始时需要先初始化参数服务器-->娃娃机主板
帧头	命令	时间		抓力控制		速度控制	预留	线长	中奖	预留	预留	帧尾
HEAD	CMD	D0	D1	D2	D3	D4	D5		D6	D7	D8	D9	D10	D11	D12	END
#	AA	30-90	1-45			1-10		0	10-90	0-1	0	0	*
D0：游戏时间 30-90S，建议值 30
D1：第一段抓力，建议值 35
D2：第二段抓力，建议值 35
D3：第三段抓力，建议值 12
D4：第四段抓力，建议值 12
D5：前后电机速度，建议值 6	数值越大速度越快
D6：左右电机速度，建议值 6	数值越大速度越快
D7：上下电机速度，建议值 6 数值越大速度越快D9：线长时间 10-90 对应 1-9S 线到底的最长时间,天车下来到底的放线长度
控制。默认 50S D10：1 中奖，0 不中奖预留：0应答：同发送

服务器：0x23 0xaa 0x1e 0x23 0x23 0x0c 0x0c 0x06 0x06 0x06 0x00 0x32 0x00 0x00 0x00 0x2a
娃娃机：0x23 0xaa 0x1e 0x23 0x23 0x0c 0x0c 0x06 0x06 0x06 0x00 0x32 0x00 0x00 0x00 0x2a

 控制娃娃机:Data[0]=0X01;Data[1]=XX; XX:BIT0~BIT4 上下左右按键服务器-->娃娃机主板 # 01 XX *
应答：同发送 （可以不应答）天车向前：

服务器：0x23 0x01 0x01 0x2a
娃娃机：0x23 0x01 0x01 0x2a

天车向后：
服务器：0x23 0x01 0x02 0x2a
娃娃机：0x23 0x01 0x02 0x2a

天车向左：
服务器：0x23 0x01 0x04 0x2a
娃娃机：0x23 0x01 0x04 0x2a

天车向右：
服务器：0x23 0x01 0x08 0x2a
娃娃机：0x23 0x01 0x08 0x2a

天车下抓：
服务器：0x23 0x01 0x10 0x2a
娃娃机：0x23 0x01 0x10 0x2a

天车不动：控制前后左右电机停止
服务器：0x23 0x01 0x00 0x2a
娃娃机：0x23 0x01 0x00 0x2a

 结果反馈:Data[0]=0X80;Data[1]=0X00/0X01(没抓到/抓到了);娃娃机主板--> 服务器 # 80 00/01 *

应答：同发送未抓中娃娃：
娃娃机：0x23 0x80 0x00 0x2a
服务器：0x23 0x80 0x00 0x2a

抓中娃娃：
娃娃机：0x23 0x80 0x01 0x2a
服务器：0x23 0x80 0x01 0x2a

查询心跳:Data[0]=0X02;Data[1]=00;服务器-->娃娃机主板 # 02 00 *
娃娃机应答：Data[0]=0X02;Data[1]=ER; # 02 ER * ER=0 系统正常，ER=1-9，系统故障

查询娃娃机是否正常运行：
服务器：0x23 0x02 00 0x2a
娃娃机：0x23 0x02 ER 0x2a

ER=0X00：正常。
ER=0X01：下抓微动或下抓电机故障。
ER=0X02：前微动或前后电机故障。
ER=0X03：后微动或前后电机故障。
ER=0X04：左微动或左右马达故障。
ER=0X05：右微动或者左右马达故障。
ER=0X06：防摇报警。
ER=0X07：礼品不足。
ER=0X08：预留。
ER=0X09：预留。
*/

public class SWawaji extends WawajiDevice {
    static final private int BAUD_RATE = 9600;

    //HEAD	CMD
    // D0	D1	D2	D3	D4
    // D5  D6	D7	D8
    // D9	D10	D11	D12
    // 	END
    /* 0x23 0xaa 0x1e 0x23 0x23 0x0c 0x0c 0x06 0x06 0x06 0x00 0x32 0x00 0x00 0x00 0x2a */
    static final private byte[] CMD_BYTE_START = {(byte) 0x23, (byte) 0xaa,(byte) 0x1e,
            (byte) 0x23,(byte) 0x23, (byte) 0x0c, (byte) 0x0c,//D1 D2 D3 D4 默认爪力最大（45 / 2D）
            (byte) 0x03, (byte) 0x03, (byte) 0x06, (byte) 0x00,
            (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x2a};    // 第3位控制时间，第13位控制是否抓中

    byte FRONT_BACK_MOVE_SPEED=(byte)5;//前后移动速度
    byte LEFT_RIGHT_MOVE_SPEED=(byte)5;//左右移动速度
    byte UP_DOWN_SPEED=(byte)5;//上下速度
    byte LINE_LENGTH=(byte)29;//线长
    /**
     *  CMD_BYTE_START[7]=(byte)0xA;//前后移动速度
     CMD_BYTE_START[8]=(byte)0xA;//左右移动速度

     CMD_BYTE_START[9]=(byte)0xA;//上下速度

     CMD_BYTE_START[11]=(byte)0x3C;//线长
     *
     */
    static final private byte[] CMD_BYTE_MOVE = {(byte) 0x23, (byte) 0x01, (byte) 0x00, (byte) 0x2a};   // 第三位控制方向
    static final private byte[] CMD_BYTE_STOP = {(byte) 0x23, (byte) 0x01, (byte) 0x00, (byte) 0x2a};
    static final private byte[] CMD_BYTE_HEART_BIT = {(byte) 0x23, (byte) 0x02, (byte) 0x00, (byte) 0x2a};

    private DeviceStateListener mListener;
    private boolean mNoMoveOperation = true;

    public SWawaji(DeviceStateListener listener) throws SecurityException, IOException {
        super(new File("/dev/ttyS1"), BAUD_RATE, Context.MODE_PRIVATE);
        mListener = listener;

        Thread readThread = new ReadThread("surui-reader");
        readThread.start();
    }

    /**
     * 设置本局游戏初始值
     *
     * @param gameTime  游戏时长
     * @param grabPower 下爪力度
     * @param upPower   提起力度
     * @param movePower 移动力度
     * @param upHeight  提起高度
     * @param seq       指令序号
     * @return 是否调用成功
     */
    @Override
    public boolean initGameConfig(int gameTime, int grabPower, int upPower, int movePower, int upHeight, int seq) {
        mNoMoveOperation = true;

        /*if (gameTime > 60 || gameTime < 10) {
            gameTime = 30;
        }

        if (grabPower > 100 || grabPower < 1) {
            grabPower = 67;
        }
        grabPower = grabPower * 48 / 100;
        if (grabPower < 1) {
            grabPower = 1;
        }

        if (upPower > 100 || upPower < 1) {
            upPower = 33;
        }
        upPower = upPower * 48 / 100;
        if (upPower < 1) {
            upPower = 1;
        }

        if (movePower > 100 || movePower < 1) {
            movePower = 21;
        }
        movePower = movePower * 48 / 100;
        if (movePower < 1) {
            movePower = 1;
        }*/

        if (grabPower > 45 || grabPower < 1) {
            grabPower = 35;
        }

        if (upPower > 45 || upPower < 1) {
            upPower = 35;
        }

        if (movePower > 45 || movePower < 1) {
            movePower = 12;
        }


        byte[] cmdData = CMD_BYTE_START;
        //cmdData[2] = (byte)gameTime;
        cmdData[3] = (byte) grabPower;                                  // 抓起爪力(1—45)，需根据实际投放的娃娃类型做现场调优
        cmdData[4] = (byte) upPower;                                    // 到顶爪力(1—45)，需根据实际投放的娃娃类型做现场调优
        cmdData[5] = (byte) movePower;                                  // 移动爪力(1—45)，需根据实际投放的娃娃类型做现场调优

        cmdData[7]=FRONT_BACK_MOVE_SPEED;//前后移动速度
        cmdData[8]=LEFT_RIGHT_MOVE_SPEED;//左右移动速度

        cmdData[9]=UP_DOWN_SPEED;//上下速度

        cmdData[11]=LINE_LENGTH;//线长

        if (grabPower >= 45 && upPower >= 45 && movePower >= 45) {
            cmdData[12] = 1;     // 1: 表示使用系统设定的爪力值
        } else {
            cmdData[12] = 0;     // 0: 表示使用指令中设定的爪力值
        }

        return sendCommandData(cmdData);
    }

    /**
     * 初始化指令数据
     *
     * @param hit      控制是否中奖，true：中奖；false：不中奖（概率）
     * @param gameTime 单局游戏时长，取值范围 [10, 60]
     * @param seq      指令序号
     * @return 初始化指令数据
     * @deprecated see {@link #initGameConfig(int, int, int, int, int, int)}
     */
    @Deprecated
    @Override
    public boolean initGameConfig(boolean hit, int gameTime, int seq) {
        mNoMoveOperation = true;

        /*if (gameTime > 60 || gameTime < 10) {
            gameTime = 30;
        }*/

        byte[] cmdData = CMD_BYTE_START;

       /* cmdData[3] = (byte) 35;// 抓起爪力(1—45)，需根据实际投放的娃娃类型做现场调优
        cmdData[4] = (byte) 35;// 到顶爪力(1—45)，需根据实际投放的娃娃类型做现场调优
        cmdData[5] = (byte) 12;// 移动爪力(1—45)，需根据实际投放的娃娃类型做现场调优*/

        cmdData[7]=FRONT_BACK_MOVE_SPEED;//前后移动速度
        cmdData[8]=LEFT_RIGHT_MOVE_SPEED;//左右移动速度

        cmdData[9]=UP_DOWN_SPEED;//上下速度

        cmdData[11]=LINE_LENGTH;//线长
        //CMD_BYTE_START[2] = (byte)gameTime;
        cmdData[12] = hit ? (byte) 1 : (byte) 0;
        return sendCommandData(cmdData);
    }

    @Override
    public boolean sendForwardCommand(int seq) {
        mNoMoveOperation = false;

        CMD_BYTE_MOVE[2] = (byte) 0x02;
        return sendCommandData(CMD_BYTE_MOVE);
    }

    @Override
    public boolean sendBackwardCommand(int seq) {
        mNoMoveOperation = false;

        CMD_BYTE_MOVE[2] = (byte) 0x01;
        return sendCommandData(CMD_BYTE_MOVE);
    }

    @Override
    public boolean sendLeftCommand(int seq) {
        mNoMoveOperation = false;

        CMD_BYTE_MOVE[2] = (byte) 0x04;
        return sendCommandData(CMD_BYTE_MOVE);
    }

    @Override
    public boolean sendRightCommand(int seq) {
        mNoMoveOperation = false;

        CMD_BYTE_MOVE[2] = (byte) 0x08;
        return sendCommandData(CMD_BYTE_MOVE);
    }

    @Override
    public boolean sendStopCommand(int seq) {
        return sendCommandData(CMD_BYTE_STOP);
    }

    @Override
    public boolean sendGrabCommand(int seq) {
        if (mNoMoveOperation) { // 初始化后没有移动过天车，需要先移动一次，否则不会下爪
            sendForwardCommand(seq);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            sendStopCommand(seq);
        }

        CMD_BYTE_MOVE[2] = (byte) 0x10;
        return sendCommandData(CMD_BYTE_MOVE);
    }

    @Override
    public boolean sendResetCommand(int seq) {
        AppLogger.getInstance().writeLog("not support reset command on swawaji device");
        return false;
    }

    @Override
    public boolean checkDeviceState() {
        return sendCommandData(CMD_BYTE_HEART_BIT);
    }

    private void onResponseCommandReceived(byte[] bufferData, int cmdLength) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmdLength; i++) {
            sb.append(Integer.toHexString((bufferData[i] & 0x000000FF) | 0xFFFFFF00).substring(6));
        }

        AppLogger.getInstance().writeLog("receive: %s from device. data size: %d", sb.toString(), cmdLength);
                switch (bufferData[1]) {
            case (byte) 0xAA:   // 开始游戏应答
                break;

            case (byte) 0x01:   // 控制移动臂指令应答
                break;

            case (byte) 0x80: {  // 是否抓到应答
                if (mListener != null) {
                    boolean win = (bufferData[2] == (byte) 0x01);
                    mListener.onGameOver(win);
                }
            }
            break;

            case (byte) 0x02:   // 查询心跳或者查询心跳应答
                AppLogger.getInstance().writeLog("娃娃机应答：ER= %s ", Integer.toHexString((bufferData[2] & 0x000000FF) | 0xFFFFFF00).substring(6));
                if (mListener != null) {
                    int errorCode = bufferData[2] & 0xff;
                    if (errorCode >= 1 && errorCode <= 9) { // 娃娃机故障
                        mListener.onDeviceStateChanged(bufferData[2]);
                    } else {
                        mListener.onDeviceStateChanged(0); // 自检无异常或者出现异常后中途又自动恢复了
                    }
                }
                break;
        }
    }

    private class ReadThread extends Thread {

        private byte[] cmdBuffer = new byte[512];   // 读取的待分析数据
        private int bufferLength = 0;
        private int currentCmdLength = 0;

        public ReadThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            int size;
            byte[] buffer = new byte[64];

            while (!isInterrupted()) {
                if (mFileInputStream == null) break;

                try {
                    size = mFileInputStream.read(buffer);

                    for (int i = 0; i < size; i++) {
                        byte b = buffer[i];
                        if (bufferLength == 0 && b != (byte) 0x23) {
                            continue;
                        }
                        cmdBuffer[bufferLength++] = buffer[i];
                    }

                    while (bufferLength >= 4) { // 包头1字节+数据位2字节+结束位1字节
                        if (cmdBuffer[0] == (byte) 0x23) {    // 合法
                            if (cmdBuffer[1] == (byte) 0xAA) {
                                currentCmdLength = 16;
                            } else {
                                currentCmdLength = 4;
                            }

                            if (bufferLength >= currentCmdLength) {
                                boolean isValidate = checkCmdData(cmdBuffer, currentCmdLength);
                                if (isValidate) {
                                    onResponseCommandReceived(cmdBuffer, currentCmdLength);
                                }

                                int j = 0;
                                for (int i = currentCmdLength; i < bufferLength; i++) {
                                    cmdBuffer[j++] = cmdBuffer[i];
                                }
                                cmdBuffer[j] = '\0';
                                bufferLength = j;
                            } else {
                                break;
                            }
                        } else {    // 不合法，查找下一个 0x23
                            int pos = -1;

                            StringBuilder tmpBuilder = new StringBuilder();
                            for (int i = 0; i < bufferLength; i++) {
                                byte b = cmdBuffer[i];
                                if (b == (byte) 0x23) {
                                    pos = i;
                                    break;
                                }
                                tmpBuilder.append(Integer.toHexString((b & 0x000000FF) | 0xFFFFFF00).substring(6));
                            }

                            AppLogger.getInstance().writeLog("**** invalid data: %s *****", tmpBuilder.toString());
                            if (pos > 0) {
                                int j = 0;
                                for (int i = pos; i < bufferLength; i++) {
                                    cmdBuffer[j++] = cmdBuffer[i];
                                }
                                cmdBuffer[j] = '\0';
                                bufferLength = j;
                            } else {
                                cmdBuffer[0] = '\0';
                                bufferLength = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    AppLogger.getInstance().writeLog("SWawaji's ReadThread Exception. e : %s", e);
                    break;
                }


                if (size < buffer.length) { // 如果不能填满缓冲区，则等待 500ms 后再读，否则立即读取下一段 buffer
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        AppLogger.getInstance().writeLog("SWawaji's ReadThread wait Exception. e : %s", e);
                    }
                }
            }
        }

        private boolean checkCmdData(byte[] data, int length) {
            return data[0] == (byte) 0x23 && data[length - 1] == (byte) 0x2A;    // 以 # 开头 以 * 结尾
        }
    }
}