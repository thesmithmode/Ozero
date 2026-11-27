from pathlib import Path
import sys


def replace_once(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f"Unexpected upstream source: {path.name}")
    path.write_text(text.replace(old, new, 1))


root = Path(sys.argv[1])
register = root / "cmd" / "register.go"
replace_once(register, '\t"log"\n', '\t"log"\n\t"os"\n')
for failure, code in (
    ("Failed to register", 20),
    ("Failed to generate key pair", 21),
    ("Failed to enroll key", 22),
    ("Failed to save config", 23),
):
    replace_once(register, f'log.Fatalf("{failure}: %v", err)', f'os.Exit({code})')
replace_once(
    root / "config" / "config.go",
    "file, err := os.Create(configPath)",
    "file, err := os.OpenFile(configPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0600)",
)
replace_once(
    root / "api" / "masque.go",
    "if len(rawCerts) == 0 {\n\t\t\t\treturn nil\n\t\t\t}",
    "if len(rawCerts) == 0 {\n\t\t\t\treturn errors.New(\"missing endpoint certificate\")\n\t\t\t}",
)
main = root / "main.go"
replace_once(main, '\t"fmt"\n', '\t"fmt"\n\t"io"\n')
replace_once(
    main,
    "func main() {\n",
    """func main() {
	if os.Getenv("OZERO_MASQUE_MANAGED") == "1" {
		go func() {
			_, _ = io.Copy(io.Discard, os.Stdin)
			os.Exit(1)
		}()
	}
""",
)
socks = root / "internal" / "socks5.go"
replace_once(socks, '\t"net"\n', '\t"net"\n\t"os"\n')
replace_once(
    socks,
    "\n\treturn srv.RunnerGroup.Wait()\n}\n",
    """
	if marker := os.Getenv("OZERO_MASQUE_READY_FILE"); marker != "" {
		if err := os.WriteFile(marker, []byte("ready"), 0600); err != nil {
			_ = l.Close()
			_ = srv.UDPConn.Close()
			return err
		}
	}
	return srv.RunnerGroup.Wait()
}
""",
)
cli = root / "cmd" / "socks.go"
replace_once(
    cli,
    '\t\tlog.Println("Hint: l4-socks is faster for TCP-only SOCKS use cases.")\n',
    """		if os.Getenv("OZERO_MASQUE_MANAGED") == "1" {
			path, err := cmd.Flags().GetString("config")
			if err != nil || os.Remove(path) != nil {
				cmd.Println("Failed to remove managed runtime config")
				return
			}
		}
		log.Println("Hint: l4-socks is faster for TCP-only SOCKS use cases.")
""",
)

# The alternate Cloudflare-owned device API is used by warpscout for the same
# two-step v0a4471 MASQUE enrolment. Do not send account data to relay services.
# Fall back to the legacy host only when no connection was acquired: never
# blindly replay a registration POST after it may have reached the server.
cloudflare = root / "api" / "cloudflare.go"
replace_once(cloudflare, '\t"net/http"\n', '\t"net/http"\n\t"net/http/httptrace"\n\t"sync/atomic"\n')
replace_once(cloudflare, 'OsVersion: "",', 'OsVersion: "10",')
source = cloudflare.read_text()
needle = "resp, err := http.DefaultClient.Do(req)"
if source.count(needle) < 2:
    raise RuntimeError("Unexpected upstream registration client")
source = source.replace(needle, "resp, err := doDeviceRegistration(req)", 2)
cloudflare.write_text(source + """
func doDeviceRegistration(req *http.Request) (*http.Response, error) {
    transport := http.DefaultTransport.(*http.Transport).Clone()
    transport.Proxy = nil
    transport.DisableKeepAlives = true
    client := &http.Client{
        Transport: transport,
        Timeout: 18 * time.Second,
        CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
            return http.ErrUseLastResponse
        },
    }
    var connected atomic.Bool
    trace := &httptrace.ClientTrace{
        GotConn: func(_ httptrace.GotConnInfo) { connected.Store(true) },
    }
    preferred := req.Clone(httptrace.WithClientTrace(req.Context(), trace))
    preferred.URL.Host = "api.devices.cloudflare.com"
    preferred.Host = preferred.URL.Host
    response, err := client.Do(preferred)
    if err == nil || connected.Load() || req.GetBody == nil {
        return response, err
    }
    body, bodyErr := req.GetBody()
    if bodyErr != nil { return nil, bodyErr }
    legacy := req.Clone(req.Context())
    legacy.Body = body
    return client.Do(legacy)
}
""")

# Enrolment responses are not guaranteed to include endpoint fields. Preserve
# the account's assigned addresses and key; use known MASQUE endpoints only
# when the API omits a usable endpoint. The peer key remains pinned.
replace_once(register, '\t"log"\n', '\t"log"\n\t"net"\n')
replace_once(
    register,
    "\n\t\tprivKey, pubKey, err := internal.GenerateEcKeyPair()",
    "\n\t\tif accountData == nil || accountData.ID == \"\" || accountData.Token == \"\" { os.Exit(24) }\n\t\tprivKey, pubKey, err := internal.GenerateEcKeyPair()",
)
replace_once(
    register,
    '\t\tlog.Printf("Successful registration. Saving config...")',
    """\t\tif updatedAccountData == nil || len(updatedAccountData.Config.Peers) == 0 ||
            updatedAccountData.Config.Peers[0].PublicKey == "" ||
            net.ParseIP(updatedAccountData.Config.Interface.Addresses.V4).To4() == nil ||
            net.ParseIP(updatedAccountData.Config.Interface.Addresses.V6) == nil ||
            net.ParseIP(updatedAccountData.Config.Interface.Addresses.V6).To4() != nil {
            os.Exit(24)
        }
        peer := updatedAccountData.Config.Peers[0]
        v4 := managedEndpointIP(peer.Endpoint.V4, false)
        v6 := managedEndpointIP(peer.Endpoint.V6, true)
        if v4 == "" { v4 = "162.159.198.1" }
        if updatedAccountData.ID == "" { updatedAccountData.ID = accountData.ID }
        log.Printf("Successful registration. Saving config...")""",
)
replace_once(
    register,
    "\t\t\t// TODO: proper endpoint parsing in utils\n"
    "\t\t\t// strip :0\n"
    "\t\t\tEndpointV4: updatedAccountData.Config.Peers[0].Endpoint.V4[:len(updatedAccountData.Config.Peers[0].Endpoint.V4)-2],\n"
    "\t\t\t// strip [ from beginning and ]:0 from end\n"
    "\t\t\tEndpointV6:     updatedAccountData.Config.Peers[0].Endpoint.V6[1 : len(updatedAccountData.Config.Peers[0].Endpoint.V6)-3],",
    "\t\t\tEndpointV4: v4,\n\t\t\tEndpointV6: v6,",
)
replace_once(
    register,
    "\n\t\tif err := config.AppConfig.SaveConfig(configPath); err != nil {",
    "\n\t\tif _, err := config.AppConfig.GetEcEndpointPublicKey(); err != nil { os.Exit(24) }\n"
    "\t\tif err := config.AppConfig.SaveConfig(configPath); err != nil {",
)
register.write_text(register.read_text() + """
func managedEndpointIP(raw string, ipv6 bool) string {
    if host, _, err := net.SplitHostPort(raw); err == nil {
        raw = host
    }
    ip := net.ParseIP(raw)
    if ip == nil || (ip.To4() == nil) != ipv6 || ip.IsUnspecified() || ip.IsLoopback() {
        return ""
    }
    return ip.String()
}
""")
