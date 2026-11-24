from pathlib import Path
import sys


def replace_once(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f"Unexpected upstream source: {path.name}")
    path.write_text(text.replace(old, new, 1))


root = Path(sys.argv[1])
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
