package libcore;

public class TunConfig {
    public int fileDescriptor;
    public int mtu;
    public V2RayInstance v2Ray;
    public boolean protect;
    public Protector protector;
    public String addr4;
    public String addr6;
    public String dns4;
    public String dns6;
    public boolean enableIPv6;
    public int implementation;
    public boolean fakeDNS;
    public boolean sniffing;
    public boolean overrideDestination;
    public boolean debug;
    public boolean dumpUID;
    public boolean trafficStats;
    public boolean pcap;
    public String protectPath;
    public boolean discardICMP;
    public boolean discardIPv6BasedOnNetwork;
}
