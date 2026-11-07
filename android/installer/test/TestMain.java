import tv.ibra.home.installer.*;
import java.io.*; import java.util.*;
public class TestMain {
  public static void main(String[] a) throws Exception {
    int port=Integer.parseInt(a[0]); File apk=new File(a[1]); File keyDir=new File(a[2]);
    final Map<String,String> m=new HashMap<String,String>();
    TvInstaller.Store st=new TvInstaller.Store(){ public String get(String k){return m.get(k);} public void put(String k,String v){m.put(k,v);} };
    TvInstaller.Log lg=new TvInstaller.Log(){ public void step(boolean ok,String s){System.out.println((ok?"OK  ":"BAD ")+s);} public void info(String s){System.out.println("..  "+s);} };
    for (String step : a[3].split(",")) {
      AdbClient.Key key=AdbClient.Key.loadOrCreate(keyDir);
      AdbClient c=AdbClient.connect("127.0.0.1",port,key,"test@pc",5000,new AdbClient.Listener(){public void onAllowPrompt(){System.out.println("..  [prompt shown on TV]");}});
      TvInstaller tv=new TvInstaller(c,"127.0.0.1",st,lg); tv.probe();
      System.out.println("== "+step);
      if(step.startsWith("install")){ boolean ok=tv.install(new FileInputStream(apk),apk.length(),null); System.out.println("install="+ok+" home="+tv.setAsHome(step.endsWith("+disable"))); }
      else System.out.println("restore="+tv.restore(step.equals("remove")));
      c.close();
    }
    System.out.println("STORE "+m);
  }
}
