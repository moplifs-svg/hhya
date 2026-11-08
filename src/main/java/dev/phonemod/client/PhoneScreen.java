package dev.phonemod.client;

import dev.phonemod.data.PhoneData;
import dev.phonemod.net.PhoneNet;
import dev.phonemod.net.PhoneSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Predicate;

public class PhoneScreen extends Screen {
    private enum View { HOME, CALL, ADD_CONTACT, MESSAGES, NEW_CHAT, NEW_GROUP, CHAT, ADD_MEMBER }

    private record Line(FormattedCharSequence seq, int color) {}

    private static final int W = 200;
    private static final int CONTACT_ROW = 22;
    private static final int CHAT_ROW = 26;
    private static final Predicate<String> NUMBER_CHARS = s -> s.matches("[0-9\\- ]*");
    private static final Predicate<String> NUMBER_LIST_CHARS = s -> s.matches("[0-9\\- ,;]*");

    private View view = View.HOME;
    private String convId = "";
    private int page = 0;
    private int chatScroll = 0;

    private int left, top, x0, innerW, contentTop, panelH;

    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, String> saved = new HashMap<>();
    private String focusKey = null;

    public PhoneScreen() {
        super(Component.literal("Phone"));
    }

    private PhoneSnapshot snap() {
        return ClientHooks.snap;
    }

    // ------------------------------------------------------------------ navigation

    /** Called when new data arrived from the server. */
    public void refresh() {
        if (!snap().focus.isEmpty()) {
            convId = snap().focus;
            go(View.CHAT);
            return;
        }
        stash();
        rebuildWidgets();
    }

    private void go(View v) {
        view = v;
        page = 0;
        chatScroll = 0;
        saved.clear();
        focusKey = null;
        ClientHooks.status = "";
        rebuildWidgets();
    }

    private void back() {
        switch (view) {
            case CALL, MESSAGES -> go(View.HOME);
            case ADD_CONTACT -> go(View.CALL);
            case NEW_CHAT, NEW_GROUP, CHAT -> go(View.MESSAGES);
            case ADD_MEMBER -> go(View.CHAT);
            default -> onClose();
        }
    }

    private void stash() {
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) {
            saved.put(e.getKey(), e.getValue().getValue());
            if (e.getValue().isFocused()) focusKey = e.getKey();
        }
    }

    // ------------------------------------------------------------------ layout helpers

    private Button btn(String label, int x, int y, int w, int h, Runnable action) {
        return Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, h).build();
    }

    private EditBox box(String key, int x, int y, int w, int max, Predicate<String> filter) {
        EditBox b = new EditBox(this.font, x, y, w, 16, Component.literal(key));
        b.setMaxLength(max);
        if (filter != null) b.setFilter(filter);
        b.setValue(saved.getOrDefault(key, ""));
        addRenderableWidget(b);
        boxes.put(key, b);
        return b;
    }

    private String val(String key) {
        EditBox b = boxes.get(key);
        return b == null ? "" : b.getValue();
    }

    private int listTop() {
        return contentTop + 14;
    }

    private int listBottom() {
        return top + panelH - 62;
    }

    private int rowsPerPage(int rowHeight) {
        return Math.max(1, (listBottom() - listTop()) / rowHeight);
    }

    private PhoneSnapshot.Conv curConv() {
        for (PhoneSnapshot.Conv c : snap().convs) if (c.id().equals(convId)) return c;
        return null;
    }

    // ------------------------------------------------------------------ build widgets

    @Override
    protected void init() {
        boxes.clear();
        panelH = Math.min(250, this.height - 6);
        left = (this.width - W) / 2;
        top = (this.height - panelH) / 2;
        x0 = left + 8;
        innerW = W - 16;

        if (view == View.CHAT && curConv() == null) view = View.MESSAGES;

        PhoneSnapshot s = snap();
        contentTop = top + 24;
        if (s.callState != 0) {
            contentTop = top + 52;
            if (s.callState == 2) {
                addRenderableWidget(btn("Answer", x0, top + 32, 90, 16, () -> callAction(0)));
                addRenderableWidget(btn("Decline", x0 + 94, top + 32, 90, 16, () -> callAction(1)));
            } else {
                addRenderableWidget(btn("Hang up", x0, top + 32, innerW, 16, () -> callAction(2)));
            }
        }

        if (view != View.HOME) addRenderableWidget(btn("Back", x0, top + panelH - 24, 56, 16, this::back));

        switch (view) {
            case HOME -> {
                addRenderableWidget(btn("Call", x0, contentTop + 40, innerW, 28, () -> go(View.CALL)));
                addRenderableWidget(btn("Messages", x0, contentTop + 76, innerW, 28, () -> go(View.MESSAGES)));
            }
            case CALL -> {
                List<PhoneSnapshot.Contact> cs = s.contacts;
                int rows = rowsPerPage(CONTACT_ROW);
                page = Mth.clamp(page, 0, Math.max(0, (cs.size() - 1) / rows));
                for (int i = 0; i < rows; i++) {
                    int idx = page * rows + i;
                    if (idx >= cs.size()) break;
                    String number = cs.get(idx).number();
                    addRenderableWidget(btn("Call", x0 + innerW - 40, listTop() + i * CONTACT_ROW + 2, 40, 16, () -> dial(number)));
                }
                addRenderableWidget(btn("Add contact", x0, top + panelH - 44, innerW, 16, () -> go(View.ADD_CONTACT)));
                pager(cs.size(), rows);
            }
            case ADD_CONTACT -> {
                box("name", x0, contentTop + 14, innerW, 16, null);
                box("number", x0, contentTop + 50, innerW, 12, NUMBER_CHARS);
                addRenderableWidget(btn("Save contact", x0, top + panelH - 44, innerW, 16, () -> {
                    PhoneNet.CH.sendToServer(new PhoneNet.AddContact(val("name"), val("number")));
                    go(View.CALL);
                }));
            }
            case MESSAGES -> {
                List<PhoneSnapshot.Conv> cv = s.convs;
                int rows = rowsPerPage(CHAT_ROW);
                page = Mth.clamp(page, 0, Math.max(0, (cv.size() - 1) / rows));
                for (int i = 0; i < rows; i++) {
                    int idx = page * rows + i;
                    if (idx >= cv.size()) break;
                    PhoneSnapshot.Conv c = cv.get(idx);
                    String title = this.font.plainSubstrByWidth((c.group() ? "[G] " : "") + c.title(), innerW - 8);
                    addRenderableWidget(btn(title, x0, listTop() + i * CHAT_ROW, innerW, 14, () -> {
                        convId = c.id();
                        go(View.CHAT);
                    }));
                }
                addRenderableWidget(btn("New chat", x0, top + panelH - 44, 90, 16, () -> go(View.NEW_CHAT)));
                addRenderableWidget(btn("New group", x0 + 94, top + panelH - 44, 90, 16, () -> go(View.NEW_GROUP)));
                pager(cv.size(), rows);
            }
            case NEW_CHAT -> {
                box("number", x0, contentTop + 14, innerW, 12, NUMBER_CHARS);
                addRenderableWidget(btn("Start chat", x0, top + panelH - 44, innerW, 16,
                        () -> PhoneNet.CH.sendToServer(new PhoneNet.StartChat(val("number")))));
            }
            case NEW_GROUP -> {
                box("gname", x0, contentTop + 14, innerW, 16, null);
                box("gnums", x0, contentTop + 50, innerW, 150, NUMBER_LIST_CHARS);
                addRenderableWidget(btn("Create group", x0, top + panelH - 44, innerW, 16,
                        () -> PhoneNet.CH.sendToServer(new PhoneNet.CreateGroup(val("gname"), val("gnums")))));
            }
            case CHAT -> {
                box("msg", x0, top + panelH - 44, innerW - 42, 256, null);
                addRenderableWidget(btn("Send", x0 + innerW - 40, top + panelH - 44, 40, 16, this::sendMsg));
                PhoneSnapshot.Conv c = curConv();
                if (c != null && c.group()) {
                    addRenderableWidget(btn("Add member", x0 + 60, top + panelH - 24, innerW - 60, 16, () -> go(View.ADD_MEMBER)));
                }
            }
            case ADD_MEMBER -> {
                box("number", x0, contentTop + 14, innerW, 12, NUMBER_CHARS);
                addRenderableWidget(btn("Add to group", x0, top + panelH - 44, innerW, 16, () -> {
                    PhoneNet.CH.sendToServer(new PhoneNet.AddMember(convId, val("number")));
                    saved.clear();
                    focusKey = null;
                    view = View.CHAT;
                    rebuildWidgets();
                }));
            }
        }

        if (focusKey != null && boxes.containsKey(focusKey)) {
            EditBox b = boxes.get(focusKey);
            setFocused(b);
            b.setFocused(true);
        }
    }

    private void pager(int total, int rows) {
        if (total <= rows) return;
        int pages = (total - 1) / rows + 1;
        addRenderableWidget(btn("<", x0 + 62, top + panelH - 24, 20, 16, () -> {
            page = Math.max(0, page - 1);
            stash();
            rebuildWidgets();
        }));
        addRenderableWidget(btn(">", x0 + 84, top + panelH - 24, 20, 16, () -> {
            page = Math.min(pages - 1, page + 1);
            stash();
            rebuildWidgets();
        }));
    }

    // ------------------------------------------------------------------ actions

    private void callAction(int action) {
        PhoneNet.CH.sendToServer(new PhoneNet.CallAction(action));
    }

    private void dial(String number) {
        PhoneNet.CH.sendToServer(new PhoneNet.Dial(number));
    }

    private void sendMsg() {
        String text = val("msg").strip();
        if (text.isEmpty()) return;
        PhoneNet.CH.sendToServer(new PhoneNet.SendMsg(convId, text));
        EditBox b = boxes.get("msg");
        if (b != null) b.setValue("");
        chatScroll = 0;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (view == View.CHAT && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            EditBox b = boxes.get("msg");
            if (b != null && b.isFocused()) {
                sendMsg();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (view == View.CHAT) {
            chatScroll += delta > 0 ? 3 : -3;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ drawing

    private String title() {
        return switch (view) {
            case HOME -> "Phone";
            case CALL -> "Call";
            case ADD_CONTACT -> "Add contact";
            case MESSAGES -> "Messages";
            case NEW_CHAT -> "New chat";
            case NEW_GROUP -> "New group";
            case ADD_MEMBER -> "Add member";
            case CHAT -> "Chat";
        };
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(this.font, this.font.plainSubstrByWidth(s, innerW), x, y, color, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + panelH + 3, 0xFF111114);
        g.fill(left, top, left + W, top + panelH, 0xFF1E2430);
        g.drawCenteredString(this.font, title(), left + W / 2, top + 8, 0xFFFFFFFF);

        PhoneSnapshot s = snap();
        if (s.callState != 0) {
            String t = switch (s.callState) {
                case 1 -> "Calling " + s.callLabel + "...";
                case 2 -> "Incoming call: " + s.callLabel;
                default -> "In call with " + s.callLabel;
            };
            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(t, innerW), left + W / 2, top + 21, 0xFFFFD27A);
        }

        switch (view) {
            case HOME -> {
                text(g, "Your number", x0, contentTop + 4, 0xFF9AA5B1);
                text(g, PhoneData.format(s.myNumber), x0, contentTop + 16, 0xFFFFFFFF);
            }
            case CALL -> {
                text(g, "Contacts", x0, contentTop + 2, 0xFF9AA5B1);
                List<PhoneSnapshot.Contact> cs = s.contacts;
                if (cs.isEmpty()) {
                    text(g, "No contacts. Add a number first.", x0, listTop() + 4, 0xFFE8E8E8);
                }
                int rows = rowsPerPage(CONTACT_ROW);
                for (int i = 0; i < rows; i++) {
                    int idx = page * rows + i;
                    if (idx >= cs.size()) break;
                    int y = listTop() + i * CONTACT_ROW;
                    g.drawString(this.font, this.font.plainSubstrByWidth(cs.get(idx).name(), innerW - 46), x0, y + 2, 0xFFFFFFFF, false);
                    g.drawString(this.font, PhoneData.format(cs.get(idx).number()), x0, y + 12, 0xFF9AA5B1, false);
                }
                pageText(g, cs.size(), rows);
            }
            case ADD_CONTACT -> {
                text(g, "Name", x0, contentTop + 3, 0xFF9AA5B1);
                text(g, "Number (10 digits)", x0, contentTop + 39, 0xFF9AA5B1);
            }
            case MESSAGES -> {
                text(g, "Conversations", x0, contentTop + 2, 0xFF9AA5B1);
                List<PhoneSnapshot.Conv> cv = s.convs;
                if (cv.isEmpty()) text(g, "No chats yet.", x0, listTop() + 4, 0xFFE8E8E8);
                int rows = rowsPerPage(CHAT_ROW);
                for (int i = 0; i < rows; i++) {
                    int idx = page * rows + i;
                    if (idx >= cv.size()) break;
                    PhoneSnapshot.Conv c = cv.get(idx);
                    String preview = "";
                    if (!c.msgs().isEmpty()) {
                        PhoneSnapshot.Msg m = c.msgs().get(c.msgs().size() - 1);
                        preview = (m.mine() ? "Me" : m.from()) + ": " + m.text();
                    }
                    g.drawString(this.font, this.font.plainSubstrByWidth(preview, innerW), x0, listTop() + i * CHAT_ROW + 15, 0xFF9AA5B1, false);
                }
                pageText(g, cv.size(), rows);
            }
            case NEW_CHAT -> text(g, "Number of the person", x0, contentTop + 3, 0xFF9AA5B1);
            case NEW_GROUP -> {
                text(g, "Group name", x0, contentTop + 3, 0xFF9AA5B1);
                text(g, "Numbers (comma separated)", x0, contentTop + 39, 0xFF9AA5B1);
            }
            case ADD_MEMBER -> text(g, "Number to add", x0, contentTop + 3, 0xFF9AA5B1);
            case CHAT -> drawChat(g);
        }

        String st = ClientHooks.status;
        if (!st.isEmpty()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(st, innerW), x0, top + panelH - 58, 0xFFFF7B7B, false);
        }

        super.render(g, mx, my, pt);
    }

    private void pageText(GuiGraphics g, int total, int rows) {
        if (total <= rows) return;
        int pages = (total - 1) / rows + 1;
        g.drawString(this.font, (page + 1) + "/" + pages, x0 + 110, top + panelH - 20, 0xFF9AA5B1, false);
    }

    private void drawChat(GuiGraphics g) {
        PhoneSnapshot.Conv c = curConv();
        if (c == null) return;
        text(g, c.title(), x0, contentTop + 2, 0xFFFFFFFF);
        if (c.group()) text(g, "With: " + c.members(), x0, contentTop + 12, 0xFF9AA5B1);

        int areaTop = contentTop + 24;
        int areaBottom = top + panelH - 62;
        g.fill(x0 - 2, areaTop - 2, x0 + innerW + 2, areaBottom, 0xFF161B24);

        List<Line> lines = new ArrayList<>();
        for (PhoneSnapshot.Msg m : c.msgs()) {
            String t = (m.mine() ? "Me" : m.from()) + ": " + m.text();
            int color = m.mine() ? 0xFF8FE3A0 : 0xFFE8E8E8;
            for (FormattedCharSequence f : this.font.split(Component.literal(t), innerW)) lines.add(new Line(f, color));
        }

        int visible = Math.max(1, (areaBottom - areaTop) / 9);
        int maxScroll = Math.max(0, lines.size() - visible);
        chatScroll = Mth.clamp(chatScroll, 0, maxScroll);
        int end = lines.size() - chatScroll;
        int start = Math.max(0, end - visible);
        int y = areaTop;
        for (int i = start; i < end; i++) {
            g.drawString(this.font, lines.get(i).seq(), x0, y, lines.get(i).color(), false);
            y += 9;
        }
    }
}
