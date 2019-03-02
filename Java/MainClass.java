import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainClass {

	public static void main(String[] args) throws IOException, InterruptedException {

		MainClass obj = new MainClass();
		obj.setupEnv(args[0]);
	}
	
	private void callScript(String fileName) throws IOException, InterruptedException {
		String ansible_run = "ansible-playbook -i /ansible_srv/inventory /ansible_srv/setup_"+fileName+".yml";
		System.out.println("Run Command:"+ansible_run);
		Process pr = Runtime.getRuntime().exec(ansible_run,null);
		pr.waitFor();

		BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));

		String line = "";

		while ((line=buf.readLine())!=null) {

		System.out.println(line);

		}
	}
	
	private void setupEnv(String filePath) throws IOException, InterruptedException {
		String jsonString = getJsonString(filePath);
		System.out.println(jsonString);
		JSONObject obj = new JSONObject(jsonString);
		String func = obj.getString("function");
		
		if(obj.has("environment")){
			callScript("envvars");
		}
		
		if("setupJenkins".equals(func)) {
			callScript("jenkins");
		}
		else {
			JSONArray dependencies = obj.getJSONArray("dependency");
			for(Object depObj : dependencies) {
				String depName = (String)depObj;
				System.out.println("Calling callScript() : "+depName);
				callScript(depName);
			}
			
			String appType = obj.getString("type");
			
			callScript(appType);
			System.out.println("Calling New callScript() : "+appType);
		}
		
		if(obj.has("job")) {
			callScript("job");
		}
		
	}
	
	private String getJsonString(String filePath) throws IOException {
		StringBuilder sb = new StringBuilder();
		File file = new File(filePath); 
		  
		BufferedReader br = new BufferedReader(new FileReader(file)); 
		  
		String st; 
		// read the file and append the whole string input of the file to sb. Seperate the lines with a '\n'
		while ((st = br.readLine()) != null) {
			sb.append(st);
		}
		
		return sb.toString();
	}

}
