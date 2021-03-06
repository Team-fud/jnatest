package cn.callback;
import cn.callback.constant.Constants;
import cn.callback.pojo.Response;
import cn.callback.pojo.ResponseBody;
import cn.callback.service.LgetLib;
import cn.callback.service.MyCallback;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * @author Sheva
 * @version 1.0
 * @date 2020/3/2 下午7:09
 * 门锁（客户端）服务
 */
public class ClientService extends Thread {

    private String cid = "1421800";
    byte[] reqID = new byte[35];


    private InputStreamReader isr;
    private BufferedReader br;
    private OutputStreamWriter osw;
    private BufferedWriter bw;

    private Socket socket;
    private ServerSocket serverSocket;
    /**
     * 读卡请求
     */
    private Response readCardResp = new Response();
    /**
     * 返回最终结果响应
     */
    private Response resultResp = new Response();

    /**
     * 配置端口
     * @param port 端口号
     */
    public ClientService(int port){
        try{
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(10000);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //读取终端发送到平台的信息
    @Override
    public void run() {
        super.run();
        try{
            System.out.println("等待终端连接....");
            socket = serverSocket.accept();
            System.out.println("终端连接成功....");
            //接收终端发送的数据
            isr = new InputStreamReader(socket.getInputStream());
            br = new BufferedReader(isr);
            //接收数据
            String str = br.readLine();
            JSONObject object = JSONObject.parseObject(str);
            //获取传递过来的数据
            String sn = object.getString("sn");
            String timestamp = object.getString("timestamp");
            String trans_code = object.getJSONObject("body").getString("trans_code");

            int seq = object.getJSONObject("body").getInteger("seq");
            System.out.println("sn: " + sn + "  timestamp: " + timestamp + "  trans_code: " + trans_code + "  seq: " + seq);
            final ResponseBody body=new ResponseBody();
            readCardResp.setSn(sn);
            readCardResp.setTimestamp(timestamp);
            body.setSeq(seq);
            readCardResp.setRsp_code("1");
            //String per_data = null;
            if ((Constants.DECODE_REQ).equals(trans_code)){
                body.setTrans_code(Constants.PLAT_TER);
                //接收到解码请求，调用so库
                int result = LgetLib.INSTANCE.JLRCs(cid, "abacadae", "98541BDA41CA",
                        reqID, 0x3D, 2, new MyCallback() {
                            public String readCard(String fid, String tidid, String resp) {
                                //设置读卡命令
                                System.out.println("此次生成的读卡命令为：　" + resp);
                                body.setRsp_data(resp);
                                readCardResp.setBody(body);
                                //发送读卡命令
                                System.out.println("发送读卡命令...");
                                System.out.println("此次发送的命令为： " + readCardResp.getBody().getRsp_data());
                                new SendRespThread(readCardResp).start();
                                //接收读卡数据
                                try{
                                    isr = new InputStreamReader(socket.getInputStream());
                                    br = new BufferedReader(isr);
                                    //返还数据
                                    String str = br.readLine();
                                    JSONObject object = JSONObject.parseObject(str);
                                    //获取传递过来的数据
                                    String sn = object.getString("sn");
                                    String timestamp = object.getString("timestamp");
                                    String trans_code = object.getJSONObject("body").getString("trans_code");
                                    int seq = object.getJSONObject("body").getInteger("seq");
                                    String req_data = object.getJSONObject("body").getString("req_data");
                                    System.out.println("终端返回的读卡数据: ");
                                    System.out.println("sn: " + sn + "  timestamp: " + timestamp + "  trans_code: " + trans_code + "  seq: " + seq + "  req_data(身份证数据): " + req_data);
                                    ResponseBody b=new ResponseBody();
                                    b.setSeq(seq);
                                    resultResp.setSn(sn);
                                    b.setTrans_code(trans_code);
                                    resultResp.setTimestamp(timestamp);
                                    resultResp.setBody(b);
                                    return req_data;
                                }catch (Exception e){
                                    System.out.println("终端返回身份数据失败...");
                                    e.printStackTrace();
                                }
                                return null;
                            }
                        }, 3);


                System.out.println("最终的结果为: " + result);
                body.setRsp_data(String.valueOf(result));
                body.setTrans_code(Constants.RESULT_CODE);
                System.out.println("开始发送结果...");
                resultResp.setBody(body);
                new SendRespThread(resultResp).start();
                //到这一步reqID已经有数据,接下来发送reqID给QuerySerivce, 在queryService中调用getInfo的so库完成结果的返回
                System.out.println("reqID: ");
                for (int i = 0; i < reqID.length; i++) {
                    System.out.println((char) reqID[i]);
                }
                new SendReqIDThread(reqID.toString()).start();
            }else{
                System.out.println("解码请求错误...");
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }
    class SendReqIDThread extends Thread{
        private String reqID;
        public SendReqIDThread(String reqID){
            this.reqID = reqID;
        }
        @Override
        public void run() {
            Socket socket = null;

            try {
                socket=new Socket("127.0.0.1",2345);
                socket.setSoTimeout(5000);

                osw = new OutputStreamWriter(socket.getOutputStream());
                bw = new BufferedWriter(osw);

                System.out.println("向服务端发送reqID...");
                char[] req = getChars(reqID.getBytes());
                //System.out.println("char reqid" + req);

                bw.write(req + "\n");
                bw.flush();
            }catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    public static char[] getChars(byte[] bytes)
    {
        Charset cs=Charset.forName("utf8");
        ByteBuffer bb=ByteBuffer.allocate(bytes.length);
        bb.put(bytes).flip();
        CharBuffer cb=cs.decode(bb);
        return cb.array();
    }

    class  SendRespThread extends Thread{
        private Response response;
        SendRespThread(Response response){
            this.response = response;
        }
        @Override
        public void run() {
            try{
                osw = new OutputStreamWriter(socket.getOutputStream());
                bw = new BufferedWriter(osw);
                //类型转换
                String data = JSON.toJSONString(response);
                bw.write(data + "\n");
                bw.flush();
            }catch (Exception e){
                System.out.println("发送命令错误....");
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        ClientService clientService = new ClientService(1234);
        clientService.start();
        //QueryService queryService = new QueryService();


    }
}
