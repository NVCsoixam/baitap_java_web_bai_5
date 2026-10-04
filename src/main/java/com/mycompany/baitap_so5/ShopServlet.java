package com.mycompany.baitap_so5;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * CD shop: list -> add to cart -> register -> cart -> checkout -> payment.
 * VNPay config via env vars VNP_TMN_CODE and VNP_HASH_SECRET (sandbox).
 */
@WebServlet(name = "ShopServlet", urlPatterns = {"", "/cart", "/register", "/checkout", "/vnpay-return"})
public class ShopServlet extends HttpServlet {

    private static final String[] NAMES = {
        "86 (the band) - True Life Songs and Pictures",
        "Paddlefoot - The first CD",
        "Paddlefoot - The second CD"};
    private static final double[] PRICES = {14.95, 12.95, 14.95};
    private static final String VNP_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    // ---------- helpers ----------
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> cart(HttpSession s) {
        Map<Integer, Integer> c = (Map<Integer, Integer>) s.getAttribute("cart");
        if (c == null) {
            c = new LinkedHashMap<>();
            s.setAttribute("cart", c);
        }
        return c;
    }

    private static double total(Map<Integer, Integer> c) {
        double t = 0;
        for (Map.Entry<Integer, Integer> e : c.entrySet()) t += PRICES[e.getKey()] * e.getValue();
        return t;
    }

    private static String money(double d) {
        return String.format(Locale.US, "%.2f", d);
    }

    private static void page(HttpServletResponse r, String title, String body) throws IOException {
        r.setContentType("text/html;charset=UTF-8");
        PrintWriter o = r.getWriter();
        o.println("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>" + esc(title) + "</title>"
                + "<style>body{font-family:Arial,sans-serif;margin:30px}h1,h3{color:#066}"
                + "table{border-collapse:collapse;width:1000px}th,td{border:1px solid #666;padding:8px;text-align:left}"
                + "th{background:#eee}form{display:inline}label{display:inline-block;width:100px;font-weight:bold}"
                + ".box{border:1px solid #ccc;background:#f8f8ff;padding:12px;width:600px}"
                + ".ok{border:1px solid #2a2;background:#e6f4ea;padding:12px;width:800px}"
                + ".err{border:1px solid #c33;background:#fdecea;padding:12px;width:800px}"
                + "input[type=text],input[type=password],input[type=email]{width:280px;padding:4px;margin:6px 0}"
                + "</style></head><body>" + body + "</body></html>");
    }

    private static String button(String path, String text, String ctx) {
        return "<form action='" + ctx + path + "' method='get'><input type='submit' value='" + text + "'></form> ";
    }

    // ---------- routing ----------
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        switch (req.getServletPath()) {
            case "/cart": showCartOrRegister(req, resp); break;
            case "/register": resp.sendRedirect(req.getContextPath() + "/cart"); break;
            case "/checkout": showCheckout(req, resp); break;
            case "/vnpay-return": vnpayReturn(req, resp); break;
            default: showList(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        switch (req.getServletPath()) {
            case "/cart": cartAction(req, resp); break;
            case "/register": register(req, resp); break;
            case "/checkout": pay(req, resp); break;
            default: doGet(req, resp);
        }
    }

    // ---------- pages ----------
    private void showList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String ctx = req.getContextPath();
        StringBuilder b = new StringBuilder("<h1>CD List</h1><table><tr><th>Description</th><th>Price</th><th></th></tr>");
        for (int i = 0; i < NAMES.length; i++) {
            b.append("<tr><td>").append(esc(NAMES[i])).append("</td><td>").append(money(PRICES[i]))
                    .append("</td><td><form action='").append(ctx).append("/cart' method='post'>")
                    .append("<input type='hidden' name='action' value='add'><input type='hidden' name='id' value='")
                    .append(i).append("'><input type='submit' value='Add To Cart'></form></td></tr>");
        }
        page(resp, "Exercise_6-1", b.append("</table>").toString());
    }

    private void showRegistration(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String ctx = req.getContextPath();
        page(resp, "Registration", "<h1>User Registration</h1>"
                + "<p>To add items to your cart, please enter your name and email address below.</p>"
                + "<form action='" + ctx + "/register' method='post'>"
                + "<label>Username:</label><input type='text' name='username' required><br>"
                + "<label>Password:</label><input type='password' name='password' required><br>"
                + "<label>Email:</label><input type='email' name='email' required><br><br>"
                + "<input type='submit' value='Register &amp; Continue'></form><br><br>"
                + button("/", "Back to CD List", ctx));
    }

    private void showCart(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String ctx = req.getContextPath();
        Map<Integer, Integer> c = cart(req.getSession());
        StringBuilder b = new StringBuilder("<h1>Your Cart</h1>");
        if (c.isEmpty()) {
            b.append("<p>Your cart is empty.</p>");
        } else {
            b.append("<table><tr><th>Description</th><th>Price</th><th>Quantity</th><th>Amount</th><th></th></tr>");
            for (Map.Entry<Integer, Integer> e : c.entrySet()) {
                int id = e.getKey();
                b.append("<tr><td>").append(esc(NAMES[id])).append("</td><td>").append(money(PRICES[id]))
                        .append("</td><td><form action='").append(ctx).append("/cart' method='post'>")
                        .append("<input type='hidden' name='action' value='update'><input type='hidden' name='id' value='").append(id).append("'>")
                        .append("<input type='text' name='qty' value='").append(e.getValue()).append("' size='3' style='width:50px'> ")
                        .append("<input type='submit' value='Update'></form></td><td>")
                        .append(money(PRICES[id] * e.getValue())).append("</td><td><form action='").append(ctx).append("/cart' method='post'>")
                        .append("<input type='hidden' name='action' value='remove'><input type='hidden' name='id' value='").append(id).append("'>")
                        .append("<input type='submit' value='Remove'></form></td></tr>");
            }
            b.append("</table>");
        }
        b.append("<br>").append(button("/", "Continue Shopping", ctx));
        if (!c.isEmpty()) b.append(button("/checkout", "Checkout", ctx));
        page(resp, "Exercise_6-1", b.toString());
    }

    private void showCartOrRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getSession().getAttribute("user") == null) showRegistration(req, resp);
        else showCart(req, resp);
    }

    private void showCheckout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession();
        String ctx = req.getContextPath();
        Map<Integer, Integer> c = cart(s);
        if (s.getAttribute("user") == null || c.isEmpty()) {
            resp.sendRedirect(ctx + "/cart");
            return;
        }
        StringBuilder b = new StringBuilder("<h1>CheckOut</h1>");
        b.append(itemsTable(c)).append("<h3 style='color:#000'>Total: $").append(money(total(c))).append("</h3>");
        b.append("<div class='box'><h3>Customer Information</h3><b>Username:</b> ").append(esc((String) s.getAttribute("user")))
                .append("<br><b>Email to receive receipt:</b> ").append(esc((String) s.getAttribute("email"))).append("</div><br>")
                .append("<form action='").append(ctx).append("/checkout' method='post'><b>Payment Method:</b> ")
                .append("<label style='width:auto;font-weight:normal'><input type='radio' name='method' value='vnpay' checked> VNPay Sandbox Gateway</label> &nbsp; ")
                .append("<label style='width:auto;font-weight:normal'><input type='radio' name='method' value='sim'> Test Simulator (Instant)</label>")
                .append("<br><br><input type='submit' value='Proceed to Payment'></form><br><br>")
                .append(button("/cart", "Back to Cart", ctx)).append(button("/", "Continue Shopping", ctx));
        page(resp, "Checkout", b.toString());
    }

    private String itemsTable(Map<Integer, Integer> c) {
        StringBuilder b = new StringBuilder("<table><tr><th>Description</th><th>Price</th><th>Quantity</th><th>Amount</th></tr>");
        for (Map.Entry<Integer, Integer> e : c.entrySet()) {
            int id = e.getKey();
            b.append("<tr><td>").append(esc(NAMES[id])).append("</td><td>").append(money(PRICES[id])).append("</td><td>")
                    .append(e.getValue()).append("</td><td>").append(money(PRICES[id] * e.getValue())).append("</td></tr>");
        }
        return b.append("</table>").toString();
    }

    // ---------- actions ----------
    private void cartAction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<Integer, Integer> c = cart(req.getSession());
        String action = req.getParameter("action");
        try {
            int id = Integer.parseInt(req.getParameter("id"));
            if (id >= 0 && id < NAMES.length && action != null) {
                switch (action) {
                    case "add": c.merge(id, 1, Integer::sum); break;
                    case "remove": c.remove(id); break;
                    case "update":
                        int q = Integer.parseInt(req.getParameter("qty").trim());
                        if (q <= 0) c.remove(id); else c.put(id, q);
                        break;
                    default:
                }
            }
        } catch (NumberFormatException ignored) {
        }
        showCartOrRegister(req, resp);
    }

    private void register(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String u = req.getParameter("username"), e = req.getParameter("email");
        if (u == null || u.isBlank() || e == null || e.isBlank()) {
            showRegistration(req, resp);
            return;
        }
        HttpSession s = req.getSession();
        s.setAttribute("user", u.trim());
        s.setAttribute("email", e.trim());
        showCart(req, resp);
    }

    private void pay(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession();
        Map<Integer, Integer> c = cart(s);
        if (s.getAttribute("user") == null || c.isEmpty()) {
            resp.sendRedirect(req.getContextPath() + "/cart");
            return;
        }
        String orderId = String.valueOf(System.currentTimeMillis());
        double amount = total(c);
        if ("sim".equals(req.getParameter("method"))) {
            showResult(req, resp, true, "Test Simulator", orderId, "SIM-" + orderId, amount, itemsTable(c));
            c.clear();
            return;
        }
        String tmn = System.getenv("VNP_TMN_CODE"), secret = System.getenv("VNP_HASH_SECRET");
        if (tmn == null || secret == null) {
            page(resp, "Checkout", "<h1>CheckOut</h1><div class='err'><h3 style='color:#a00'>VNPay not configured</h3>"
                    + "Set environment variables VNP_TMN_CODE and VNP_HASH_SECRET, or use Test Simulator.</div><br>"
                    + button("/checkout", "Back", req.getContextPath()));
            return;
        }
        s.setAttribute("pendingOrder", orderId);
        s.setAttribute("pendingTable", itemsTable(c));
        String proto = req.getHeader("X-Forwarded-Proto"), host = req.getHeader("X-Forwarded-Host");
        if (host == null) host = req.getHeader("Host");
        if (proto == null) proto = req.getScheme();
        String base = proto.split(",")[0].trim() + "://" + host.split(",")[0].trim() + req.getContextPath();
        // Amount is in USD in the cart; VNPay needs VND * 100. Assume 1 USD = 25000 VND.
        long vnd = Math.round(amount * 25000) * 100;
        TreeMap<String, String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", tmn);
        p.put("vnp_Amount", String.valueOf(vnd));
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", orderId);
        p.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", base + "/vnpay-return");
        p.put("vnp_IpAddr", req.getRemoteAddr());
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss");
        f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        p.put("vnp_CreateDate", f.format(new Date()));
        String q = queryString(p);
        resp.sendRedirect(VNP_URL + "?" + q + "&vnp_SecureHash=" + hmac(secret, q));
    }

    private void vnpayReturn(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession();
        String secret = System.getenv("VNP_HASH_SECRET");
        TreeMap<String, String> p = new TreeMap<>();
        for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
            if (e.getKey().startsWith("vnp_") && !e.getKey().equals("vnp_SecureHash") && !e.getKey().equals("vnp_SecureHashType"))
                p.put(e.getKey(), e.getValue()[0]);
        }
        boolean valid = secret != null && hmac(secret, queryString(p)).equalsIgnoreCase(req.getParameter("vnp_SecureHash"));
        boolean ok = valid && "00".equals(req.getParameter("vnp_ResponseCode"));
        double amount = 0;
        try {
            amount = Long.parseLong(p.getOrDefault("vnp_Amount", "0")) / 100.0 / 25000.0;
        } catch (NumberFormatException ignored) {
        }
        String table = (String) s.getAttribute("pendingTable");
        showResult(req, resp, ok, "VNPay Sandbox", p.getOrDefault("vnp_TxnRef", ""),
                p.getOrDefault("vnp_TransactionNo", ""), amount, table == null ? "" : table);
        if (ok) cart(s).clear();
    }

    private void showResult(HttpServletRequest req, HttpServletResponse resp, boolean ok, String method,
            String orderId, String txn, double amount, String table) throws IOException {
        HttpSession s = req.getSession();
        StringBuilder b = new StringBuilder("<h1>Order &amp; Payment Status</h1>");
        if (ok) {
            b.append("<div class='ok'><h2 style='color:#186a2b'>Payment Successful!</h2>")
                    .append("<p>Thank you, <b>").append(esc((String) s.getAttribute("user"))).append("</b>. Your order has been placed and paid successfully.</p>")
                    .append("<p><b>Order ID:</b> #").append(esc(orderId)).append("</p>")
                    .append("<p><b>Transaction Reference:</b> ").append(esc(txn)).append("</p>")
                    .append("<p><b>Payment Method:</b> ").append(method).append("</p>")
                    .append("<p><b>Total Amount Paid:</b> $").append(money(amount)).append("</p>")
                    .append(emailNotice(s, orderId, method, txn, amount, table)).append("</div>")
                    .append("<h3 style='color:#000'>Purchased Items</h3>").append(table);
        } else {
            b.append("<div class='err'><h2 style='color:#a00'>Payment Failed</h2><p>The payment was cancelled or the signature is invalid.</p></div>");
        }
        b.append("<br>").append(button("/", "Continue Shopping", req.getContextPath()));
        page(resp, "Payment Result", b.toString());
    }

    private String emailNotice(HttpSession s, String orderId, String method, String txn, double amount, String table) {
        String key = System.getenv("BREVO_API_KEY"), sender = System.getenv("BREVO_SENDER_EMAIL");
        String to = (String) s.getAttribute("email"), user = (String) s.getAttribute("user");
        if (key == null || sender == null || to == null) {
            return "<p style='color:#b35900'><b>Email Notice:</b> Payment is recorded. Email was not sent "
                    + "(environment variables BREVO_API_KEY/BREVO_SENDER_EMAIL not configured).</p>";
        }
        String html = "<h2>Payment Successful</h2><p>Thank you, " + esc(user) + ".</p><p>Order ID: #" + esc(orderId)
                + "<br>Transaction: " + esc(txn) + "<br>Method: " + esc(method) + "<br>Total: $" + money(amount)
                + "</p>" + table.replace("<table>", "<table border='1' cellpadding='6' style='border-collapse:collapse'>");
        String json = "{\"sender\":{\"name\":\"CD Shop\",\"email\":" + jsonStr(sender) + "},"
                + "\"to\":[{\"email\":" + jsonStr(to) + ",\"name\":" + jsonStr(user) + "}],"
                + "\"subject\":" + jsonStr("Payment receipt - Order #" + orderId) + ","
                + "\"htmlContent\":" + jsonStr(html) + "}";
        try {
            java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.brevo.com/v3/smtp/email"))
                            .header("api-key", key).header("content-type", "application/json").header("accept", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) {
                return "<p style='color:#186a2b'><b>Email Notice:</b> Receipt sent to " + esc(to) + ".</p>";
            }
            return "<p style='color:#a00'><b>Email Notice:</b> Payment is recorded but email failed (HTTP "
                    + r.statusCode() + "): " + esc(r.body()) + "</p>";
        } catch (Exception e) {
            return "<p style='color:#a00'><b>Email Notice:</b> Payment is recorded but email failed: " + esc(String.valueOf(e)) + "</p>";
        }
    }

    private static String jsonStr(String v) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (v == null ? "" : v).toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String queryString(TreeMap<String, String> p) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
                    .append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    private static String hmac(String key, String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA512");
            m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            StringBuilder sb = new StringBuilder();
            for (byte x : m.doFinal(data.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
