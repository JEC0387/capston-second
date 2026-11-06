import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Diagnostic transport only. Never the app's production HTTPS/authentication path. */
public final class LoopbackWireSender {
  static String sha(byte[] bytes) throws Exception {
    StringBuilder value=new StringBuilder();
    for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format("%02x",b&255));
    return value.toString();
  }
  static byte[] readBounded(Path path,int maximum) throws IOException {
    long size=Files.size(path);
    if(size<1 || size>maximum) throw new IOException("input_size_limit");
    ByteArrayOutputStream bytes=new ByteArrayOutputStream((int)Math.min(size,16384));
    try(InputStream in=Files.newInputStream(path)){
      byte[] buffer=new byte[16384];int count;
      while((count=in.read(buffer))!=-1){
        if(count>maximum-bytes.size()) throw new IOException("input_grew_past_limit");
        bytes.write(buffer,0,count);
      }
    }
    if(bytes.size()<1) throw new IOException("empty_input");
    return bytes.toByteArray();
  }
  public static void main(String[] args) throws Exception {
    if(args.length!=3 || !args[0].matches("http://127\\.0\\.0\\.1:[0-9]{4,5}/p57/validate")) throw new SecurityException("loopback_only");
    Path bodyPath=Paths.get(args[1]).toRealPath(), tokenPath=Paths.get(args[2]).toRealPath();
    if(!bodyPath.toString().startsWith("/data/local/tmp/p57-") || !tokenPath.toString().startsWith("/data/local/tmp/p57-")) throw new SecurityException("owned_fixture_only");
    byte[] body=readBounded(bodyPath,29*1024*1024);
    String token=new String(readBounded(tokenPath,128),StandardCharsets.US_ASCII).trim();
    if(!token.matches("[a-f0-9]{64}")) throw new SecurityException("diagnostic_capability_invalid");
    String bodySha=sha(body);
    HttpURLConnection connection=(HttpURLConnection)new URL(args[0]).openConnection();
    connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(30000);
    connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);
    connection.setRequestProperty("Content-Type","application/json");
    connection.setRequestProperty("Authorization","Bearer "+token);
    connection.setRequestProperty("X-P57-Body-SHA256",bodySha);
    try {
      try(OutputStream out=connection.getOutputStream()){out.write(body);}
      int status=connection.getResponseCode();
      if(status!=200) throw new IOException("validation_http_"+status);
      ByteArrayOutputStream response=new ByteArrayOutputStream();
      try(InputStream in=connection.getInputStream()){
        byte[] buffer=new byte[1024];int n;
        while((n=in.read(buffer))!=-1){if(response.size()+n>4096)throw new IOException("response_limit");response.write(buffer,0,n);}
      }
      JSONObject result=new JSONObject(new String(response.toByteArray(),StandardCharsets.UTF_8));
      if(!"PASS".equals(result.opt("status")) || !bodySha.equals(result.opt("received_body_sha256")))
        throw new IOException("validation_receipt_mismatch");
      Object calls=result.opt("external_provider_calls");
      if(!(calls instanceof Integer || calls instanceof Long) || ((Number)calls).longValue()!=0)
        throw new IOException("unexpected_provider_calls");
      // Emit only the checked receipt fields, never an arbitrary server response or token.
      System.out.println(new JSONObject().put("status","PASS").put("received_body_sha256",bodySha)
        .put("external_provider_calls",0).toString());
    } finally {connection.disconnect();}
  }
}
