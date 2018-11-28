package com.ytools.tomcatMonitor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;


public class TomcatNonitor {
	
	//TODO 打包时替换
//	private static final String CONF_PATH = "I:\\debug\\yunyi_workspace\\tomcatMonitor\\src\\conf.properties"; 
	private static final String CONF_PATH = "conf.properties";
	private static final String DEFAULT_TIMER = "1";

	private JLabel label_2;
	private JFrame frmTomcat;
	private JTextField textField_timer;
	private JTextField textField_url;
	private JTextField textField_path;
	private JButton button_1;
	private JButton button_3;
	
	private JScrollPane scrollPane;
	private JTable table;
	private DefaultTableModel tableModel;
	private int selectedRow;
	private int failTime = 0;
	
	private int timerMin = 0;
	private boolean stopMonitor = true;
	private JLabel label_4;
	
	
	private JTextField textField_port;
	private JTextField textField_Time;
	private JTextPane contentPanel;
	private JScrollPane jsp;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					TomcatNonitor window = new TomcatNonitor();
					window.frmTomcat.setVisible(true);
					window.exitSaveProperty();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public void exitSaveProperty(){
		this.frmTomcat.addWindowListener(new WindowAdapter() {//关闭时写入配置文件
			public void windowClosing(WindowEvent e) {
				String timer = textField_timer.getText();
				String restartTime = textField_Time.getText();
				int rowCount = tableModel.getRowCount();
				StringBuilder urls  = new StringBuilder();
				StringBuilder paths = new StringBuilder();
				StringBuilder ports = new StringBuilder();
				for(int a = 0; a < rowCount; a++){
					String url  = (String)tableModel.getValueAt(a, 0);
					urls.append(url).append(",");
					
					String path = (String)tableModel.getValueAt(a, 1);
					paths.append(path).append(",");
					
					String port = (String)tableModel.getValueAt(a, 2);
					ports.append(port).append(",");
				}
				if(urls.length() > 0){
					urls.deleteCharAt(urls.length() - 1);
					paths.deleteCharAt(paths.length() - 1);
					ports.deleteCharAt(ports.length() - 1);
					Properties pro = new Properties();
					try {
						pro.setProperty("timer", timer);
						pro.setProperty("restartTime", restartTime);
						pro.setProperty("urls", urls.toString());
						pro.setProperty("paths", paths.toString());
						pro.setProperty("ports", ports.toString());
						FileOutputStream outputFile = new FileOutputStream(CONF_PATH);  
						pro.store(outputFile, "modify");  
			            outputFile.flush();  
			            outputFile.close();  
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
				super.windowClosing(e);
			 }  
		});
	}

	/**
	 * Create the application.
	 */
	public TomcatNonitor() {
		initialize();
		//启动定时任务
		startTimer();
	}
	
	/**
	 * http 监控
	 */
	private void startCheckTimer(){
		final int rowCount = tableModel.getRowCount();
		if(rowCount == 0 || stopMonitor) return;
		
		Timer checkTimer = new Timer();
		checkTimer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				if(stopMonitor) return;
				for(int a = 0; a < rowCount; a++){
					String url  = (String)tableModel.getValueAt(a, 0);
					String path = (String)tableModel.getValueAt(a, 1);
					String port = (String)tableModel.getValueAt(a, 2);
					httpMonitor(url, path, port);
				}
				startCheckTimer();
			}
		},  
		//延迟执行毫秒数
		Integer.parseInt(textField_timer.getText()) * 60 * 1000);
	}
	/**
	 * 重启任务
	 */
	private void startRestartTimer(){
		final int rowCount = tableModel.getRowCount();
		String restartTime = textField_Time.getText();
		if(rowCount == 0 || stopMonitor || !isNotNullAndEmpty(restartTime)) return;
		
		Calendar triggerDate = Calendar.getInstance();
		triggerDate.set(Calendar.HOUR_OF_DAY, Integer.parseInt(restartTime));
		triggerDate.set(Calendar.MINUTE, 0);
		triggerDate.set(Calendar.SECOND, 10);
		
		if(Calendar.getInstance().getTime().getTime() > triggerDate.getTime().getTime()){
			triggerDate.add(Calendar.DAY_OF_MONTH, 1);
		}
		
		Timer restartTimer = new Timer();
		restartTimer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				if(stopMonitor) return;
				
				stopMonitor = true;
				for(int a = 0; a < rowCount; a++){
					String path = (String)tableModel.getValueAt(a, 1);
					String port = (String)tableModel.getValueAt(a, 2);
					appendLog(String.format("%s 时间到，执行重启计划", port));
					restartBat(path, port);
				}
				try {
					Thread.sleep(10 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				stopMonitor = false;
				startRestartTimer();
			}
		},  
		//指定时间执行
		triggerDate.getTime());
	}
	
	private void bindBtnTimer(){
		button_1.addActionListener(new ActionListener() {//开始监控事件
			public void actionPerformed(ActionEvent arg0) {
				stopMonitor = false;
				appendLog("开始监控");
				String timer = textField_timer.getText();
				if(timer != null && timer.length() > 0){
					timerMin = Integer.parseInt(timer) * 60;//换算为秒
					
					startCheckTimer();
					startRestartTimer();
				}
			}
		});
		button_3.addActionListener(new ActionListener() {//停止监控事件
			public void actionPerformed(ActionEvent e) {
				stopMonitor = true;
				appendLog("监控已停止");
			}
		});
	}
	
	/**
	 * 启动定时任务器
	 */
	private void startTimer(){
		Runnable runnable = new Runnable() {  
            public void run() {  
        	   executeTimer();
            }  
        };  
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();  
        // 第二个参数为首次执行的延时时间，第三个参数为定时执行的间隔时间 ，第四个参数为单位
        service.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.SECONDS);
	}
	
	private void executeTimer(){
		if(!stopMonitor){
			if(timerMin > 0){//监控中：时间倒数
				button_1.setEnabled(false);
				button_1.setText("监控中（" + timerMin + "s)");
				timerMin--;
			} else {//监控中：时间到
				timerMin = Integer.parseInt(textField_timer.getText()) * 60;//换算为秒
			}
		} else {
			button_1.setText("开始监控");
			button_1.setEnabled(true);
		}
	}
	private boolean isNotNullAndEmpty(String arg){
		return arg != null && !"".equals(arg.trim());
	}
	
	/**
	 * @param batPath
	 * @param port
	 */
	private void restartBat(String path, String port){

		//1:find port's pId : netstat -aon|findstr "8080"
		
		String pid = null;
		List<String> pids = executeCommand("cmd.exe /C netstat -aon|findstr \"" + port + "\"", true);
		if(pids != null){
			for(String pidStr : pids){
				if(pidStr != null){
					String pidArray[] = pidStr.trim().split("       ");
					if(pidArray.length > 3){
						if("LISTENING".equals(pidArray[3].trim())){
							pid = pidArray[pidArray.length - 1];
						}
					}
				}
			}
		}
		//2:taskkill by pid : taskkill /PID 9112
		if(pid != null){
			System.out.println(pid);
			executeCommand("cmd.exe /C taskkill /f /PID " + pid, false);
		}
	
		//3:start bat

		int dirIndex = path.indexOf(":");
		int lastPathIndex = path.lastIndexOf("\\");
		executeCommand("cmd.exe /C "
				+ " " + path.substring(0, dirIndex + 1)  
				+ " && cd " + path.substring(0, lastPathIndex).replace("\\", "\\\\") 
				+ " && " + path.substring(lastPathIndex + 1) 
				, false);
	}
	/**
	 * http 监控检查
	 */
	public void httpMonitor(String url, String path, String port){
		int responseCode = -1;
		try {
			responseCode = this.urlRequest(url, "GET");
			if(responseCode < 200 ) {//直接挂掉
				throw new Exception(url + "访问失败");
			} else if(responseCode > 500){//504
				this.appendLog(url + " code=" + responseCode + " 访问失败，自动重启...");
				restartBat(path, port);
			}
		} catch (Exception e) {
			this.appendLog(url + " code=" + responseCode + " 访问失败，自动重启...");
			failTime++;
			label_4.setText(String.valueOf(failTime));
			e.printStackTrace();
			int dirIndex = path.indexOf(":");
			int lastPathIndex = path.lastIndexOf("\\");
			executeCommand("cmd.exe /C "
					+ " " + path.substring(0, dirIndex + 1)  
					+ " && cd " + path.substring(0, lastPathIndex).replace("\\", "\\\\") 
					+ " && " + path.substring(lastPathIndex + 1) 
					, 
					false);
		}
	}
	//cmd 参考 http://blog.csdn.net/zhang103886108/article/details/44809215
	public List<String> executeCommand(String command, boolean readResponse) {  
        //System.out.println(command); 
        Runtime r = Runtime.getRuntime();  
        Process p = null;  
        List<String> str = new ArrayList<String>();
        try {
            p = r.exec(command);
            if(readResponse){
	            BufferedReader read = new BufferedReader(new InputStreamReader(p.getInputStream()));
	            String linStr;
	    		while(null != (linStr = read.readLine())){
	    			str.add(new String(linStr.getBytes(), "UTF-8"));
	    		}
            }
    		p.getOutputStream().close();
        } catch (IOException e) {  
            e.printStackTrace();
            appendLog("cmd 异常" + e.getMessage());
        }  
        return str;
    }  
	/**
	 * @param requestUrl
	 * @param method
	 * @return
	 * @throws Exception
	 */
	protected int urlRequest(String requestUrl, String method) throws Exception{
 
		URL url = new URL(requestUrl.toString());
		// 打开url连接
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		// 设置url请求方式 ‘get’ 或者 ‘post’
		connection.setRequestMethod(method);
		connection.setReadTimeout(10 * 1000);
		connection.setConnectTimeout(10 * 1000);
		// 发送
		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
		// 返回发送结果
		StringBuilder inputline = new StringBuilder();
		String linStr;
		while(null != (linStr = in.readLine())){
			inputline.append(new String(linStr.getBytes(), "UTF-8"));
		}
		int responseCode = connection.getResponseCode();
		in.close();
		connection.disconnect();
		return responseCode;
	}
	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmTomcat = new JFrame();
		frmTomcat.setResizable(false);
		frmTomcat.setTitle("HTTP 监控 & 重启 -Ytools");
		frmTomcat.setBounds(100, 100, 903, 344);
		frmTomcat.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmTomcat.getContentPane().setLayout(null);
		frmTomcat.setLocationRelativeTo(null);//居中
		frmTomcat.setIconImage(Toolkit.getDefaultToolkit().getImage(TomcatNonitor.class.getResource("/favicon.jpg"))); 
		
		JLabel lblurl = new JLabel("监控URL");
		lblurl.setBounds(40, 13, 66, 15);
		frmTomcat.getContentPane().add(lblurl);
		
		textField_url = new JTextField();
		textField_url.setBounds(92, 10, 382, 21);
		frmTomcat.getContentPane().add(textField_url);
		textField_url.setColumns(10);
		
		JLabel label_1 = new JLabel("启动程序路径");
		label_1.setBounds(10, 41, 96, 15);
		frmTomcat.getContentPane().add(label_1);
		
		textField_path = new JTextField();
		textField_path.setBounds(92, 38, 488, 21);
		frmTomcat.getContentPane().add(textField_path);
		textField_path.setColumns(10);
		
		JButton button = new JButton("添加至监控列表");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(textField_url.getText().trim().length() > 0 && 
						textField_path.getText().trim().length() > 0){
					tableModel.addRow(new Object[]{
							textField_url.getText(), 
							textField_path.getText(),
							textField_port.getText(),
							textField_Time.getText()
							});
				}
			}
		});
		button.setBounds(20, 63, 311, 37);
		frmTomcat.getContentPane().add(button);
		
		JLabel label = new JLabel("每");
		label.setBounds(301, 271, 26, 24);
		frmTomcat.getContentPane().add(label);
		
		textField_timer = new JTextField();
		textField_timer.setHorizontalAlignment(SwingConstants.CENTER);
		textField_timer.setBounds(323, 269, 36, 26);
		textField_timer.setText(DEFAULT_TIMER);
		frmTomcat.getContentPane().add(textField_timer);
		textField_timer.setColumns(10);
		
		button_1 = new JButton("开始监控");
		button_3 = new JButton("停止监控");
		bindBtnTimer();
		
		button_1.setForeground(Color.WHITE);
		button_1.setBackground(Color.BLUE);
		button_1.setBounds(484, 258, 147, 37);
		
		frmTomcat.getContentPane().add(button_1);
		

		textField_Time = new JTextField();
		textField_Time.setText("3");
		textField_Time.setHorizontalAlignment(SwingConstants.CENTER);
		textField_Time.setColumns(10);
		textField_Time.setBounds(176, 271, 36, 26);
		frmTomcat.getContentPane().add(textField_Time);
		
		Object[][] data = null;
		
		
		Properties pro = new Properties();
		try {
			pro.load(new FileInputStream(new File(CONF_PATH)));
			String timer = pro.getProperty("timer");
			String urls  = pro.getProperty("urls");
			String paths = pro.getProperty("paths");
			String ports = pro.getProperty("ports");
			String restartTime = pro.getProperty("restartTime");
			if(timer != null && timer.length() > 0){
				textField_timer.setText(timer);
			}
			if(restartTime != null && restartTime.length() > 0){
				textField_Time.setText(restartTime);
			}
			if(urls != null && urls.length() > 0){
				String urlArray []  = urls.split(",");
				String pathArray[]  = paths.split(",");
				
				String portsArray[] = new String[urlArray.length];
				if(ports != null && !"".equals(ports.trim())){
					portsArray = ports.split(",");
				}
				data = new Object[urlArray.length][];
				int a = 0;
				for(String url : urlArray){
					data[a] = new Object[]{url, pathArray[a], portsArray[a] };
					a++;
				}
			}
		} catch (Exception e1) {
			e1.printStackTrace();
			this.appendLog(e1.getMessage());
		}
		
		table = new JTable(data, new Object[]{"url", "path", "port" });
		tableModel = new DefaultTableModel(
			data,
			new String[] {
				"url", "path", "port" 
			}
		);
		table.setModel(tableModel);
		table.getColumnModel().getColumn(0).setPreferredWidth(300);
		table.getColumnModel().getColumn(1).setPreferredWidth(200);
		
		table.addMouseListener(new MouseAdapter(){    //鼠标事件
            public void mouseClicked(MouseEvent e){
                selectedRow = table.getSelectedRow(); //获得选中行索引
            }
        });
		
		scrollPane = new JScrollPane(table);
		scrollPane.setPreferredSize(new Dimension(688, 122));
		
		JPanel jPanel = new JPanel();
		jPanel.setBounds(30, 110, 688, 122);
		jPanel.setBackground(Color.white);
		jPanel.add(scrollPane);
		
		frmTomcat.getContentPane().add(jPanel);
		
		JButton button_2 = new JButton("删除监控");
		button_2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(selectedRow > -1){
					tableModel.removeRow(selectedRow);
					selectedRow = -1;
				}
			}
		});
		button_2.setBounds(402, 63, 311, 37);
		frmTomcat.getContentPane().add(button_2);
		
		button_3.setForeground(Color.WHITE);
		button_3.setBackground(Color.RED);
		button_3.setBounds(641, 258, 89, 37);
		frmTomcat.getContentPane().add(button_3);
		
		label_4 = new JLabel("0");
		label_4.setToolTipText("失败次数");
		label_4.setFont(new Font("宋体", Font.BOLD, 28));
		label_4.setForeground(Color.RED);
		label_4.setBounds(92, 261, 44, 34);
		frmTomcat.getContentPane().add(label_4);
		
		JLabel label_5 = new JLabel("端口");
		label_5.setBounds(500, 13, 66, 15);
		frmTomcat.getContentPane().add(label_5);
		
		JLabel lblx = new JLabel("每天");
		lblx.setBounds(146, 274, 26, 18);
		frmTomcat.getContentPane().add(lblx);
		
		JLabel label_6 = new JLabel("点，定时重启");
		label_6.setBounds(212, 274, 81, 21);
		frmTomcat.getContentPane().add(label_6);
		
		textField_port = new JTextField();
		textField_port.setColumns(10);
		textField_port.setBounds(535, 10, 45, 21);
		frmTomcat.getContentPane().add(textField_port);
		
		
		jsp = new JScrollPane();
		jsp.setBounds(740, 13, 147, 282);
		
		contentPanel = new JTextPane();
		
		JViewport vp = new JViewport();
		vp.add(contentPanel);
		jsp.setViewport(vp);
		
		frmTomcat.getContentPane().add(jsp);
		
		JLabel lblNewLabel = new JLabel("异常重启次数");
		lblNewLabel.setBounds(10, 274, 85, 15);
		frmTomcat.getContentPane().add(lblNewLabel);
		
		label_2 = new JLabel("分钟，监控一次");
		label_2.setBounds(369, 274, 105, 15);
		frmTomcat.getContentPane().add(label_2);
		appendLog("系统启动");
	}
	
	/**
	 * @param msg
	 */
	public void appendLog(String msg){
		if(contentPanel == null) return;
		
		String text = new SimpleDateFormat("MM-dd HH:mm").format(new Date()) + " " + msg;
		text += "\r\n \r\n";
		
		//设置字体大小
        SimpleAttributeSet attrset = new SimpleAttributeSet();
        StyleConstants.setFontSize(attrset, 12);
          
        //插入内容
        Document docs = contentPanel.getDocument();//获得文本对象
        try {
            docs.insertString(docs.getLength(), text, attrset);//对文本进行追加
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        JScrollBar sBar = jsp.getVerticalScrollBar(); //得到了该JScrollBar    
        sBar.setValue(sBar.getMaximum());  //设置一个具体位置，value为具体的位置    
	}
}
