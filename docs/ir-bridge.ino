// -----------------------------------------------------------------------------
// 红外桥 —— 给「中央空调」app 用的红外发射/学习小板
//
// app 只算波形，发射交给这块板子。把它放在墙上线控器附近，红外灯对准面板上那个
// 小黑点（接收窗），通电后 app 填它的 IP 就能用。
//
// 需要的库（Arduino IDE → 库管理器里搜）：
//   * IRremoteESP8266   （ESP8266 和 ESP32 都用这个）
//   * ArduinoJson       （6.x 或 7.x）
//
// 接线（ESP8266 / NodeMCU）：
//   红外发射管 —— 220Ω 电阻 —— D2 (GPIO4)，负极接 GND
//   红外接收头 (VS1838B 之类) —— OUT 接 D5 (GPIO14)，VCC 3V3，GND
// ESP32 把下面的 PIN 换成你板子上的引脚号即可。
//
// 接口：
//   POST /send     {"carrier":38000,"raw":[9000,4500,620,...]}   发一帧
//   GET  /capture?timeout=8000                                   学一帧，返回 raw
//   GET  /                                                        看状态
//
// /capture 是给「app 里的码表你家线控器不认」这种情况用的：拿你原配遥控器对着
// 接收头按一下，把返回的 raw 数组存下来，就能原样重放。
// -----------------------------------------------------------------------------

#if defined(ESP8266)
  #include <ESP8266WiFi.h>
  #include <ESP8266WebServer.h>
  ESP8266WebServer server(80);
  const uint16_t PIN_IR_SEND = 4;   // D2
  const uint16_t PIN_IR_RECV = 14;  // D5
#else
  #include <WiFi.h>
  #include <WebServer.h>
  WebServer server(80);
  const uint16_t PIN_IR_SEND = 4;
  const uint16_t PIN_IR_RECV = 14;
#endif

#include <IRsend.h>
#include <IRrecv.h>
#include <IRutils.h>
#include <ArduinoJson.h>

// ---- 改成你家的 Wi-Fi ----------------------------------------------------------
const char* WIFI_SSID = "你的WiFi名字";
const char* WIFI_PASS = "你的WiFi密码";
// ------------------------------------------------------------------------------

IRsend irsend(PIN_IR_SEND);

const uint16_t kCaptureBufferSize = 1024;
const uint8_t  kTimeoutMs = 50;      // 一帧结束判定
IRrecv irrecv(PIN_IR_RECV, kCaptureBufferSize, kTimeoutMs, true);
decode_results capture;

uint32_t framesSent = 0;

void sendJson(int code, const JsonDocument& doc) {
  String out;
  serializeJson(doc, out);
  server.send(code, "application/json", out);
}

void sendError(int code, const char* message) {
  JsonDocument doc;
  doc["ok"] = false;
  doc["error"] = message;
  sendJson(code, doc);
}

// POST /send —— app 主要用的就是这个
void handleSend() {
  if (server.method() != HTTP_POST) return sendError(405, "use POST");
  if (!server.hasArg("plain")) return sendError(400, "empty body");

  JsonDocument doc;
  if (deserializeJson(doc, server.arg("plain"))) return sendError(400, "bad json");

  JsonArrayConst raw = doc["raw"].as<JsonArrayConst>();
  if (raw.isNull() || raw.size() < 4) return sendError(400, "raw missing or too short");
  if (raw.size() > 700) return sendError(400, "raw too long");

  uint16_t carrier = doc["carrier"] | 38000;
  uint16_t repeat  = doc["repeat"]  | 1;
  if (repeat > 3) repeat = 3;

  static uint16_t buf[700];
  uint16_t n = 0;
  for (JsonVariantConst v : raw) buf[n++] = (uint16_t)v.as<uint32_t>();

  for (uint16_t r = 0; r < repeat; r++) {
    irrecv.disableIRIn();                       // 别录到自己发的
    irsend.sendRaw(buf, n, carrier / 1000);     // 库要的是 kHz
    irrecv.enableIRIn();
    if (r + 1 < repeat) delay(60);
  }
  framesSent++;

  JsonDocument out;
  out["ok"] = true;
  out["pulses"] = n;
  out["carrier"] = carrier;
  sendJson(200, out);
}

// GET /capture —— 学一帧原始码，用来对付码表不认的线控器
void handleCapture() {
  uint32_t timeout = server.hasArg("timeout") ? server.arg("timeout").toInt() : 8000;
  if (timeout < 1000) timeout = 1000;
  if (timeout > 30000) timeout = 30000;

  uint32_t deadline = millis() + timeout;
  while (millis() < deadline) {
    if (irrecv.decode(&capture)) {
      JsonDocument out;
      out["ok"] = true;
      out["protocol"] = typeToString(capture.decode_type);
      out["bits"] = capture.bits;
      JsonArray arr = out["raw"].to<JsonArray>();
      // rawbuf[0] 是上一帧结束到这一帧开头的间隔，不要。
      for (uint16_t i = 1; i < capture.rawlen; i++) {
        arr.add((uint32_t)capture.rawbuf[i] * kRawTick);
      }
      irrecv.resume();
      sendJson(200, out);
      return;
    }
    delay(5);
    server.handleClient();   // 别把网页卡死
  }
  sendError(408, "no ir frame received");
}

void handleRoot() {
  JsonDocument out;
  out["ok"] = true;
  out["device"] = "hvacpanel-ir-bridge";
  out["ip"] = WiFi.localIP().toString();
  out["rssi"] = WiFi.RSSI();
  out["uptime_s"] = millis() / 1000;
  out["frames_sent"] = framesSent;
  sendJson(200, out);
}

void setup() {
  Serial.begin(115200);
  irsend.begin();
  irrecv.enableIRIn();

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("连接 Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) { delay(400); Serial.print("."); }
  Serial.printf("\n就绪，地址 http://%s\n", WiFi.localIP().toString().c_str());
  Serial.println("把这个地址填到 app 的「红外桥地址」里。");

  server.on("/", HTTP_GET, handleRoot);
  server.on("/send", handleSend);
  server.on("/capture", HTTP_GET, handleCapture);
  server.onNotFound([]() { sendError(404, "no such endpoint"); });
  server.begin();
}

void loop() {
  server.handleClient();
}
