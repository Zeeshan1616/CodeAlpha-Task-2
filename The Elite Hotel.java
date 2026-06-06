import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// ══════════════════════════════════════════════════════════════
//  MODELS
// ══════════════════════════════════════════════════════════════

class Room implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum RoomType {
        STANDARD, DELUXE, SUITE;
        public double getPrice() { return this==STANDARD?80:this==DELUXE?150:300; }
        public String getDesc()  { return this==STANDARD?"Essential amenities, cozy comfort":this==DELUXE?"Premium furnishings, city view":"Luxury suite, jacuzzi & butler"; }
    }
    private final int number; private final RoomType type;
    public Room(int n, RoomType t) { number=n; type=t; }
    public int      getNumber() { return number; }
    public RoomType getType()   { return type; }
    public double   getPrice()  { return type.getPrice(); }
}

class Reservation implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Status { CONFIRMED, CANCELLED }
    private final int id; private final String guest, contact; private final int roomNum;
    private final Room.RoomType rType; private final LocalDate checkIn, checkOut;
    private final double total; private Status status; private boolean paid;
    Reservation(int id,String g,String c,Room r,LocalDate ci,LocalDate co) {
        this.id=id; guest=g; contact=c; roomNum=r.getNumber(); rType=r.getType();
        checkIn=ci; checkOut=co; total=ChronoUnit.DAYS.between(ci,co)*r.getPrice();
        status=Status.CONFIRMED; paid=false;
    }
    public int           getId()      { return id; }
    public String        getGuest()   { return guest; }
    public String        getContact() { return contact; }
    public int           getRoomNum() { return roomNum; }
    public Room.RoomType getRType()   { return rType; }
    public LocalDate     getCheckIn() { return checkIn; }
    public LocalDate     getCheckOut(){ return checkOut; }
    public double        getTotal()   { return total; }
    public Status        getStatus()  { return status; }
    public boolean       isPaid()     { return paid; }
    public void setStatus(Status s)   { status=s; }
    public void setPaid(boolean p)    { paid=p; }
    public long getNights()           { return ChronoUnit.DAYS.between(checkIn,checkOut); }
}

// ══════════════════════════════════════════════════════════════
//  STORAGE
// ══════════════════════════════════════════════════════════════

class DataStore {
    private static final String BASE   = System.getProperty("user.home")+File.separator+".elitehotel";
    private static final String R_FILE = BASE+File.separator+"reservations.dat";
    private static final String M_FILE = BASE+File.separator+"rooms.dat";
    DataStore() { new File(BASE).mkdirs(); }
    @SuppressWarnings("unchecked")
    private <T> T load(String path, T def) {
        File f=new File(path); if(!f.exists()) return def;
        try(ObjectInputStream o=new ObjectInputStream(new FileInputStream(f))){ return (T)o.readObject(); }
        catch(Exception e){ return def; }
    }
    private void save(String path,Object obj) {
        try(ObjectOutputStream o=new ObjectOutputStream(new FileOutputStream(path))){ o.writeObject(obj); }
        catch(IOException e){ e.printStackTrace(); }
    }
    public Map<Integer,Room>  loadRooms()                       { return load(M_FILE,new HashMap<>()); }
    public void               saveRooms(Map<Integer,Room> m)    { save(M_FILE,m); }
    public List<Reservation>  loadReservations()                { return load(R_FILE,new ArrayList<>()); }
    public void               saveReservations(List<Reservation> l){ save(R_FILE,l); }
    public void               clearReservations()               { new File(R_FILE).delete(); }
}

// ══════════════════════════════════════════════════════════════
//  SERVICE
// ══════════════════════════════════════════════════════════════

class HotelService {
    private final Map<Integer,Room> rooms;
    private List<Reservation> reservations;
    private final DataStore store;
    private int nextId=2001;
    public HotelService() {
        store=new DataStore(); rooms=store.loadRooms(); reservations=store.loadReservations();
        if(rooms.isEmpty()) initRooms();
        reservations.forEach(r->{ if(r.getId()>=nextId) nextId=r.getId()+1; });
    }
    private void initRooms() {
        for(int i=101;i<=110;i++) rooms.put(i,new Room(i,Room.RoomType.STANDARD));
        for(int i=201;i<=207;i++) rooms.put(i,new Room(i,Room.RoomType.DELUXE));
        for(int i=301;i<=303;i++) rooms.put(i,new Room(i,Room.RoomType.SUITE));
        store.saveRooms(rooms);
    }
    public List<Room> searchAvailable(Room.RoomType t,LocalDate ci,LocalDate co) {
        Set<Integer> occ=reservations.stream()
            .filter(r->r.getStatus()==Reservation.Status.CONFIRMED)
            .filter(r->r.getCheckIn().isBefore(co)&&ci.isBefore(r.getCheckOut()))
            .map(Reservation::getRoomNum).collect(Collectors.toSet());
        return rooms.values().stream()
            .filter(r->t==null||r.getType()==t).filter(r->!occ.contains(r.getNumber()))
            .sorted(Comparator.comparingInt(Room::getNumber)).collect(Collectors.toList());
    }
    public List<Room> allRooms() {
        return rooms.values().stream().sorted(Comparator.comparingInt(Room::getNumber)).collect(Collectors.toList());
    }
    public Reservation book(String g,String c,int rn,LocalDate ci,LocalDate co) {
        Room r=rooms.get(rn);
        if(r==null) throw new IllegalArgumentException("Room "+rn+" does not exist.");
        if(searchAvailable(null,ci,co).stream().noneMatch(x->x.getNumber()==rn))
            throw new IllegalStateException("Room "+rn+" is not available for those dates.");
        Reservation res=new Reservation(nextId++,g,c,r,ci,co);
        reservations.add(res); store.saveReservations(reservations); return res;
    }
    public boolean cancel(int id) {
        return reservations.stream()
            .filter(r->r.getId()==id&&r.getStatus()==Reservation.Status.CONFIRMED)
            .findFirst().map(r->{ r.setStatus(Reservation.Status.CANCELLED); store.saveReservations(reservations); return true; })
            .orElse(false);
    }
    public boolean pay(int id,String card) {
        if(!card.replaceAll("\\s","").matches("\\d{16}"))
            throw new IllegalArgumentException("Card number must be exactly 16 digits.");
        return reservations.stream()
            .filter(r->r.getId()==id&&r.getStatus()==Reservation.Status.CONFIRMED)
            .findFirst().map(r->{ if(r.isPaid()) throw new IllegalStateException("Already paid."); r.setPaid(true); store.saveReservations(reservations); return true; })
            .orElse(false);
    }
    public List<Reservation> byGuest(String n) {
        return reservations.stream().filter(r->r.getGuest().toLowerCase().contains(n.toLowerCase())).collect(Collectors.toList());
    }
    public List<Reservation> allReservations() { return Collections.unmodifiableList(reservations); }
    public void clearAllRecords() { reservations.clear(); nextId=2001; store.clearReservations(); }
}

// ══════════════════════════════════════════════════════════════
//  DESIGN TOKENS
// ══════════════════════════════════════════════════════════════

class DS {
    static final Color BG=new Color(0xF4F2ED),PANEL=new Color(0xFFFFFF),GOLD=new Color(0xB8934A),
        GOLD_H=new Color(0x9A7A38),CHARCOAL=new Color(0x1A1A1A),TEXT=new Color(0x111111),
        MUTED=new Color(0x777370),BORDER=new Color(0xD8D2C8),ROW_ALT=new Color(0xF0EDE6),
        ROW_SEL=new Color(0xDEEAF5),RED=new Color(0xC0392B),GREEN=new Color(0x27744A),
        BLUE=new Color(0x2C5282),BLUE_H=new Color(0x1E3A5F),
        SG1=new Color(0x181818),SG2=new Color(0x252525),
        SA1=new Color(0x0F2035),SA2=new Color(0x1A3A5C);
    static final Font TITLE=new Font("Georgia",Font.BOLD,26),SUB=new Font("Georgia",Font.ITALIC,12),
        HEADING=new Font("Georgia",Font.BOLD,17),
        BODY=new Font("SansSerif",Font.PLAIN,13),BODY_B=new Font("SansSerif",Font.BOLD,13),
        TBL=new Font("SansSerif",Font.PLAIN,13),TBL_H=new Font("SansSerif",Font.BOLD,12),
        SMALL=new Font("SansSerif",Font.PLAIN,11),BTN=new Font("SansSerif",Font.BOLD,13),
        STAT=new Font("Georgia",Font.BOLD,24);
    static final DateTimeFormatter FMT=DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final String LINE="─".repeat(42);
}

// ══════════════════════════════════════════════════════════════
//  WIDGETS
// ══════════════════════════════════════════════════════════════

class RoundBtn extends JButton {
    private final Color n,h,f; private boolean hov;
    RoundBtn(String t,Color bg,Color hov,Color fg){
        super(t); n=bg; h=hov; f=fg;
        setFocusPainted(false); setContentAreaFilled(false); setBorderPainted(false);
        setFont(DS.BTN); setForeground(fg); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(getPreferredSize().width+20,40));
        addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){RoundBtn.this.hov=true; repaint();}
            public void mouseExited (MouseEvent e){RoundBtn.this.hov=false;repaint();}
        });
    }
    @Override protected void paintComponent(Graphics g){
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(hov?h:n); g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
        g2.setColor(f); g2.setFont(getFont());
        FontMetrics fm=g2.getFontMetrics();
        g2.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
        g2.dispose();
    }
}

class FlatField extends JTextField {
    private final String ph;
    FlatField(String p,int c){ super(c); ph=p; setFont(DS.BODY); setForeground(DS.TEXT); setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(new LineBorder(DS.BORDER,1),BorderFactory.createEmptyBorder(7,11,7,11)));
        setPreferredSize(new Dimension(0,38)); }
    @Override protected void paintComponent(Graphics g){ super.paintComponent(g);
        if(getText().isEmpty()&&!isFocusOwner()){ Graphics2D g2=(Graphics2D)g; g2.setColor(new Color(0xAAAAAA)); g2.setFont(DS.BODY);
        FontMetrics fm=g2.getFontMetrics(); g2.drawString(ph,12,(getHeight()+fm.getAscent()-fm.getDescent())/2); } }
}

class FlatPass extends JPasswordField {
    FlatPass(int c){ super(c); setFont(DS.BODY); setForeground(DS.TEXT); setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(new LineBorder(DS.BORDER,1),BorderFactory.createEmptyBorder(7,11,7,11)));
        setPreferredSize(new Dimension(0,38)); }
}

class Card extends JPanel {
    Card(){ setBackground(DS.PANEL); setBorder(BorderFactory.createCompoundBorder(
        new LineBorder(DS.BORDER,1),BorderFactory.createEmptyBorder(20,24,20,24))); }
}

class PageHeader extends JPanel {
    PageHeader(String title,String sub){
        setOpaque(false); setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        JSeparator sep=new JSeparator(); sep.setForeground(DS.BORDER); sep.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        add(lbl(title,DS.TITLE,DS.CHARCOAL)); add(Box.createVerticalStrut(3));
        add(lbl(sub,DS.SUB,DS.MUTED));        add(Box.createVerticalStrut(10));
        add(sep); add(Box.createVerticalStrut(2));
    }
    static JLabel lbl(String t,Font f,Color c){
        JLabel l=new JLabel(t); l.setFont(f); l.setForeground(c); l.setAlignmentX(Component.LEFT_ALIGNMENT); return l;
    }
}

class StatCard extends Card {
    StatCard(String label,String value,Color accent){
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        JPanel bar=new JPanel(){ @Override protected void paintComponent(Graphics g){ g.setColor(accent); g.fillRect(0,0,getWidth(),4); } };
        bar.setPreferredSize(new Dimension(0,4)); bar.setMaximumSize(new Dimension(Integer.MAX_VALUE,4)); bar.setOpaque(false);
        add(bar); add(Box.createVerticalStrut(14));
        add(PageHeader.lbl(value,DS.STAT,DS.TEXT)); add(Box.createVerticalStrut(4));
        add(PageHeader.lbl(label,DS.SMALL,DS.MUTED));
    }
}

// ══════════════════════════════════════════════════════════════
//  TABLE FACTORY
// ══════════════════════════════════════════════════════════════

class TF {
    static JTable make(DefaultTableModel model){
        DefaultTableCellRenderer cr=new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean foc,int row,int col){
                super.getTableCellRendererComponent(t,v==null?"":v.toString(),sel,foc,row,col);
                setFont(DS.TBL); setForeground(DS.TEXT); setBorder(BorderFactory.createEmptyBorder(0,10,0,10));
                setBackground(sel?DS.ROW_SEL:row%2==0?DS.PANEL:DS.ROW_ALT);
                if(sel) setForeground(DS.CHARCOAL); return this;
            }
        };
        JTable tbl=new JTable(model){ @Override public TableCellRenderer getCellRenderer(int r,int c){ return cr; } };
        tbl.setRowHeight(32); tbl.setShowGrid(false); tbl.setShowHorizontalLines(true);
        tbl.setGridColor(new Color(0xEAE6E0)); tbl.setIntercellSpacing(new Dimension(0,0));
        tbl.setSelectionBackground(DS.ROW_SEL); tbl.setSelectionForeground(DS.CHARCOAL);
        tbl.setFillsViewportHeight(true); tbl.setBackground(DS.PANEL);
        JTableHeader h=tbl.getTableHeader(); h.setFont(DS.TBL_H); h.setBackground(DS.CHARCOAL);
        h.setForeground(Color.WHITE); h.setPreferredSize(new Dimension(0,34));
        h.setDefaultRenderer(new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean s,boolean f,int r,int c){
                super.getTableCellRendererComponent(t,v,s,f,r,c);
                setBackground(DS.CHARCOAL); setForeground(Color.WHITE); setFont(DS.TBL_H);
                setHorizontalAlignment(SwingConstants.LEFT); setBorder(BorderFactory.createEmptyBorder(0,10,0,10));
                return this;
            }
        });
        return tbl;
    }
    static JScrollPane scroll(JTable t){
        JScrollPane sp=new JScrollPane(t); sp.setBorder(new LineBorder(DS.BORDER,1)); sp.getViewport().setBackground(DS.PANEL); return sp;
    }
}

// ══════════════════════════════════════════════════════════════
//  BASE DASHBOARD  (shared helpers)
// ══════════════════════════════════════════════════════════════

abstract class Base extends JFrame {
    protected final HotelService svc;
    protected JPanel main; protected CardLayout cl; protected JButton activeNav;
    Base(HotelService s,String title,int w,int h){
        svc=s; setTitle(title); setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(w,h); setLocationRelativeTo(null);
    }
    protected JPanel page(){ JPanel p=new JPanel(new BorderLayout(0,18)); p.setBackground(DS.BG); p.setBorder(BorderFactory.createEmptyBorder(30,30,30,30)); return p; }
    protected JLabel fl(String t){ JLabel l=new JLabel(t); l.setFont(DS.BODY_B); l.setForeground(DS.TEXT); return l; }
    protected void err(String m){ JOptionPane.showMessageDialog(this,m,"Error",JOptionPane.ERROR_MESSAGE); }
    protected void ok (String m){ JOptionPane.showMessageDialog(this,m,"Success",JOptionPane.INFORMATION_MESSAGE); }
    protected void inf(String m){ JOptionPane.showMessageDialog(this,m,"Info",JOptionPane.INFORMATION_MESSAGE); }
    protected JTextArea receipt(){ JTextArea a=new JTextArea(9,50); a.setEditable(false);
        a.setFont(new Font("Monospaced",Font.PLAIN,12)); a.setForeground(DS.TEXT);
        a.setBackground(new Color(0xF5F2EC)); a.setBorder(BorderFactory.createEmptyBorder(12,14,12,14)); return a; }
    protected JScrollPane receiptScroll(JTextArea a){ JScrollPane sp=new JScrollPane(a);
        sp.setBorder(new LineBorder(DS.BORDER,1)); sp.getViewport().setBackground(new Color(0xF5F2EC)); return sp; }
    protected JComboBox<String> combo(String...items){ JComboBox<String> c=new JComboBox<>(items);
        c.setFont(DS.BODY); c.setBackground(Color.WHITE); c.setForeground(DS.TEXT); c.setPreferredSize(new Dimension(0,38)); return c; }
    protected DefaultTableModel tableModel(String...cols){ return new DefaultTableModel(cols,0){ public boolean isCellEditable(int r,int c){ return false; } }; }
    protected void setNav(JButton b,Color col){
        if(activeNav!=null){ activeNav.setForeground(new Color(0xBBBBBB)); activeNav.setFont(DS.BODY); }
        activeNav=b; b.setForeground(col); b.setFont(DS.BODY_B);
    }
    protected JButton navBtn(String label,String card,Color ac){
        JButton b=new JButton("  "+label); b.setOpaque(false); b.setContentAreaFilled(false); b.setBorderPainted(false);
        b.setFont(DS.BODY); b.setForeground(new Color(0xBBBBBB)); b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));
        b.setBorder(BorderFactory.createEmptyBorder(0,18,0,0));
        b.addActionListener(e->{ cl.show(main,card); setNav(b,ac); });
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){ if(b!=activeNav) b.setForeground(Color.WHITE); }
            public void mouseExited (MouseEvent e){ if(b!=activeNav) b.setForeground(new Color(0xBBBBBB)); }
        });
        return b;
    }
    protected JButton logoutBtn(){ JButton b=new JButton("  Logout");
        b.setOpaque(false); b.setContentAreaFilled(false); b.setBorderPainted(false);
        b.setFont(DS.SMALL); b.setForeground(new Color(0x777777)); b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
        b.setBorder(BorderFactory.createEmptyBorder(0,20,0,0));
        b.addActionListener(e->{ dispose(); new LoginScreen(svc).setVisible(true); });
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){ b.setForeground(Color.WHITE); }
            public void mouseExited (MouseEvent e){ b.setForeground(new Color(0x777777)); }
        });
        return b;
    }
    protected JPanel sidebar(Color g1,Color g2,Color ac,String portal,java.util.function.Consumer<JPanel> populate){
        JPanel sb=new JPanel(){ @Override protected void paintComponent(Graphics g){
            Graphics2D g2d=(Graphics2D)g.create();
            g2d.setPaint(new GradientPaint(0,0,g1,0,getHeight(),g2)); g2d.fillRect(0,0,getWidth(),getHeight());
            g2d.setColor(ac); g2d.fillRect(getWidth()-2,0,2,getHeight()); g2d.dispose(); } };
        sb.setPreferredSize(new Dimension(215,0)); sb.setLayout(new BoxLayout(sb,BoxLayout.Y_AXIS));
        JPanel hdr=new JPanel(); hdr.setOpaque(false); hdr.setLayout(new BoxLayout(hdr,BoxLayout.Y_AXIS));
        hdr.setBorder(BorderFactory.createEmptyBorder(28,22,20,22));
        JLabel h1=PageHeader.lbl("The Elite Hotel",new Font("Georgia",Font.BOLD,15),Color.WHITE);
        JLabel h2=PageHeader.lbl(portal,DS.SMALL,ac);
        hdr.add(h1); hdr.add(Box.createVerticalStrut(2)); hdr.add(h2);
        JSeparator sep=new JSeparator(); sep.setForeground(new Color(50,50,50)); sep.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        sb.add(hdr); sb.add(sep); sb.add(Box.createVerticalStrut(10));
        populate.accept(sb);
        return sb;
    }
}

// ══════════════════════════════════════════════════════════════
//  LOGIN SCREEN
// ══════════════════════════════════════════════════════════════

class LoginScreen extends JFrame {
    private final HotelService svc; private CardLayout cl; private JPanel cards;
    LoginScreen(HotelService svc){ this.svc=svc; setTitle("The Elite Hotel — Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(820,540); setLocationRelativeTo(null); setResizable(false);
        JPanel root=new JPanel(new BorderLayout()); root.setBackground(DS.BG);
        root.add(buildLeft(),BorderLayout.WEST); root.add(buildRight(),BorderLayout.CENTER);
        setContentPane(root);
    }
    private JPanel buildLeft(){
        JPanel left=new JPanel(null){ @Override protected void paintComponent(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0,0,DS.SG1,0,getHeight(),DS.SG2)); g2.fillRect(0,0,getWidth(),getHeight());
            g2.setColor(DS.GOLD); g2.fillRect(getWidth()-3,0,3,getHeight());
            g2.setColor(new Color(200,169,110,18));
            g2.fillOval(-80,-80,260,260); g2.fillOval(getWidth()-120,getHeight()-120,260,260); g2.dispose(); } };
        left.setPreferredSize(new Dimension(270,0));
        JPanel c=new JPanel(); c.setOpaque(false); c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));
        c.setBorder(BorderFactory.createEmptyBorder(56,38,40,32));
        JPanel div=new JPanel(); div.setMaximumSize(new Dimension(140,1));
        div.setBackground(new Color(DS.GOLD.getRed(),DS.GOLD.getGreen(),DS.GOLD.getBlue(),80)); div.setOpaque(true);
        c.add(PageHeader.lbl("E",new Font("Georgia",Font.BOLD,48),DS.GOLD));
        c.add(Box.createVerticalStrut(14));
        c.add(PageHeader.lbl("<html>The Elite<br>Hotel</html>",new Font("Georgia",Font.BOLD,28),Color.WHITE));
        c.add(Box.createVerticalStrut(10));
        c.add(PageHeader.lbl("<html><i>Where Comfort Meets Excellence</i></html>",new Font("Georgia",Font.ITALIC,12),DS.GOLD));
        c.add(Box.createVerticalStrut(20)); c.add(div); c.add(Box.createVerticalStrut(14));
        c.add(PageHeader.lbl("20 Rooms  ·  3 Categories",DS.SMALL,new Color(0xAAAAAA)));
        left.setLayout(new BorderLayout()); left.add(c,BorderLayout.CENTER); return left;
    }
    private JPanel buildRight(){
        JPanel right=new JPanel(new BorderLayout(0,16)); right.setBackground(DS.BG);
        right.setBorder(BorderFactory.createEmptyBorder(36,36,36,36));
        JPanel tabRow=new JPanel(new GridLayout(1,2,6,0)); tabRow.setOpaque(false);
        JToggleButton gTab=mkTab("Guest Login"),aTab=mkTab("Admin Login");
        new ButtonGroup(){{add(gTab);add(aTab);}}; gTab.setSelected(true); act(gTab); deact(aTab);
        tabRow.add(gTab); tabRow.add(aTab);
        cards=new JPanel(); cl=new CardLayout(); cards.setLayout(cl); cards.setOpaque(false);
        cards.add(loginCard("Welcome Back","Sign in to manage your reservations",
            "guest","1234","Username","Password","Sign In",DS.GOLD,DS.GOLD_H,
            ()->{ dispose(); new GuestDashboard(svc).setVisible(true); },
            "Default credentials: guest / 1234"),"guest");
        cards.add(loginCard("Admin Portal","Authorized personnel only",
            "admin","admin123","Admin ID","Password","Access Portal",DS.BLUE,DS.BLUE_H,
            ()->{ dispose(); new AdminDashboard(svc).setVisible(true); },
            "Default credentials: admin / admin123"),"admin");
        gTab.addActionListener(e->{ cl.show(cards,"guest"); act(gTab); deact(aTab); });
        aTab.addActionListener(e->{ cl.show(cards,"admin"); act(aTab); deact(gTab); });
        right.add(tabRow,BorderLayout.NORTH); right.add(cards,BorderLayout.CENTER); return right;
    }
    private JPanel loginCard(String title,String sub,String defU,String defP,String ul,String pl,
            String btnTxt,Color bg,Color hover,Runnable onOk,String hint){
        JPanel wrap=new JPanel(new GridBagLayout()); wrap.setOpaque(false);
        Card card=new Card(); card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS)); card.setPreferredSize(new Dimension(330,320));
        FlatField uf=new FlatField(defU,20); uf.setText(defU); uf.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));
        FlatPass  pf=new FlatPass(20);       pf.setText(defP); pf.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));
        RoundBtn btn=new RoundBtn(btnTxt,bg,hover,Color.WHITE); btn.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));
        btn.addActionListener(e->{
            if(uf.getText().trim().equals(defU)&&new String(pf.getPassword()).equals(defP)) onOk.run();
            else JOptionPane.showMessageDialog(this,"Invalid credentials.","Access Denied",JOptionPane.ERROR_MESSAGE);
        });
        card.add(PageHeader.lbl(title,DS.TITLE,DS.CHARCOAL)); card.add(Box.createVerticalStrut(3));
        card.add(PageHeader.lbl(sub,DS.SUB,DS.MUTED));         card.add(Box.createVerticalStrut(22));
        card.add(fl(ul)); card.add(Box.createVerticalStrut(5)); card.add(uf);
        card.add(Box.createVerticalStrut(12));
        card.add(fl(pl)); card.add(Box.createVerticalStrut(5)); card.add(pf);
        card.add(Box.createVerticalStrut(5)); card.add(PageHeader.lbl(hint,DS.SMALL,DS.MUTED));
        card.add(Box.createVerticalStrut(18)); card.add(btn);
        wrap.add(card); return wrap;
    }
    private JToggleButton mkTab(String t){ JToggleButton b=new JToggleButton(t);
        b.setFont(DS.BODY_B); b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b; }
    private void act  (JToggleButton b){ b.setBackground(DS.CHARCOAL); b.setForeground(Color.WHITE); b.setOpaque(true); }
    private void deact(JToggleButton b){ b.setBackground(DS.BORDER);   b.setForeground(DS.MUTED);    b.setOpaque(true); }
    private JLabel fl(String t){ JLabel l=new JLabel(t); l.setFont(DS.BODY_B); l.setForeground(DS.TEXT); l.setAlignmentX(Component.LEFT_ALIGNMENT); return l; }
}

// ══════════════════════════════════════════════════════════════
//  GUEST DASHBOARD
// ══════════════════════════════════════════════════════════════

class GuestDashboard extends Base {
    GuestDashboard(HotelService svc){
        super(svc,"The Elite Hotel — Guest",1060,680);
        main=new JPanel(); cl=new CardLayout(); main.setLayout(cl); main.setBackground(DS.BG);
        main.add(searchPanel(),"search"); main.add(bookPanel(),"book");
        main.add(myBookingsPanel(),"mybookings"); main.add(payPanel(),"pay");
        JPanel root=new JPanel(new BorderLayout());
        root.add(sidebar(DS.SG1,DS.SG2,DS.GOLD,"Guest Portal",sb->{
            sb.add(navBtn("Search Rooms","search",DS.GOLD));
            sb.add(navBtn("Book a Room","book",DS.GOLD));
            sb.add(navBtn("My Bookings","mybookings",DS.GOLD));
            sb.add(navBtn("Pay for Booking","pay",DS.GOLD));
            sb.add(Box.createVerticalGlue()); sb.add(logoutBtn()); sb.add(Box.createVerticalStrut(16));
        }),BorderLayout.WEST);
        root.add(main,BorderLayout.CENTER); setContentPane(root); cl.show(main,"search");
    }

    private JPanel searchPanel(){
        JPanel p=page(); p.add(new PageHeader("Search Available Rooms","Find the perfect room for your stay"),BorderLayout.NORTH);
        Card form=new Card(); form.setLayout(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(5,6,5,6); c.fill=GridBagConstraints.HORIZONTAL;
        JComboBox<String> tb=combo("All Types","Standard ($80/night)","Deluxe ($150/night)","Suite ($300/night)");
        FlatField ci=new FlatField("yyyy-MM-dd",12); ci.setText(LocalDate.now().format(DS.FMT));
        FlatField co=new FlatField("yyyy-MM-dd",12); co.setText(LocalDate.now().plusDays(2).format(DS.FMT));
        RoundBtn sb=new RoundBtn("Search",DS.GOLD,DS.GOLD_H,Color.WHITE);
        c.gridx=0;c.weightx=0.05;form.add(fl("Room Type"),c); c.gridx=1;c.weightx=0.3;form.add(tb,c);
        c.gridx=2;c.weightx=0.05;form.add(fl("Check-In"),c);  c.gridx=3;c.weightx=0.2;form.add(ci,c);
        c.gridx=4;c.weightx=0.05;form.add(fl("Check-Out"),c); c.gridx=5;c.weightx=0.2;form.add(co,c);
        c.gridx=6;c.weightx=0.1;form.add(sb,c);
        DefaultTableModel model=tableModel("Room No.","Type","Price/Night","Description");
        JTable table=TF.make(model);
        sb.addActionListener(e->{ try{
            LocalDate cin=LocalDate.parse(ci.getText().trim(),DS.FMT),cout=LocalDate.parse(co.getText().trim(),DS.FMT);
            if(!cout.isAfter(cin)){ err("Check-out must be after check-in."); return; }
            int sel=tb.getSelectedIndex();
            Room.RoomType t=sel==1?Room.RoomType.STANDARD:sel==2?Room.RoomType.DELUXE:sel==3?Room.RoomType.SUITE:null;
            List<Room> rooms=svc.searchAvailable(t,cin,cout); model.setRowCount(0);
            rooms.forEach(r->model.addRow(new Object[]{String.valueOf(r.getNumber()),r.getType().name(),"$"+(int)r.getPrice(),r.getType().getDesc()}));
            if(rooms.isEmpty()) inf("No rooms available for the selected criteria.");
        }catch(Exception ex){ err("Invalid date format. Use yyyy-MM-dd"); } });
        JPanel center=new JPanel(new BorderLayout(0,14)); center.setOpaque(false);
        center.add(form,BorderLayout.NORTH); center.add(TF.scroll(table),BorderLayout.CENTER);
        p.add(center,BorderLayout.CENTER); return p;
    }

    private JPanel bookPanel(){
        JPanel p=page(); p.add(new PageHeader("Book a Room","Reserve your stay at The Elite Hotel"),BorderLayout.NORTH);
        Card form=new Card(); form.setLayout(new GridLayout(0,2,14,12));
        FlatField name=new FlatField("Full name",20),contact=new FlatField("Email or phone",20),
            room=new FlatField("e.g. 201",6),
            ci=new FlatField("yyyy-MM-dd",12),co=new FlatField("yyyy-MM-dd",12);
        ci.setText(LocalDate.now().format(DS.FMT)); co.setText(LocalDate.now().plusDays(2).format(DS.FMT));
        form.add(fl("Guest Name")); form.add(name); form.add(fl("Contact / Email")); form.add(contact);
        form.add(fl("Room Number")); form.add(room); form.add(fl("Check-In Date")); form.add(ci); form.add(fl("Check-Out Date")); form.add(co);
        RoundBtn btn=new RoundBtn("Confirm Booking",DS.GOLD,DS.GOLD_H,Color.WHITE); btn.setMaximumSize(new Dimension(200,40));
        JTextArea rec=receipt(); rec.setText("  Your booking confirmation will appear here.");
        btn.addActionListener(e->{ try{
            String n=name.getText().trim(),cnt=contact.getText().trim(),rm=room.getText().trim();
            if(n.isEmpty()||cnt.isEmpty()||rm.isEmpty()){ err("Please fill in all fields."); return; }
            LocalDate cin=LocalDate.parse(ci.getText().trim(),DS.FMT),cout=LocalDate.parse(co.getText().trim(),DS.FMT);
            if(!cout.isAfter(cin)){ err("Check-out must be after check-in."); return; }
            Reservation res=svc.book(n,cnt,Integer.parseInt(rm),cin,cout);
            rec.setText(gReceipt(res)); ok("Booking confirmed! ID: #"+res.getId());
        }catch(NumberFormatException ex){ err("Enter a valid room number (e.g. 201)."); }
         catch(Exception ex){ err(ex.getMessage()); } });
        JPanel btnRow=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)); btnRow.setOpaque(false); btnRow.add(btn);
        JPanel top=new JPanel(new BorderLayout(0,10)); top.setOpaque(false); top.add(form,BorderLayout.NORTH); top.add(btnRow,BorderLayout.SOUTH);
        JPanel center=new JPanel(new BorderLayout(0,14)); center.setOpaque(false);
        center.add(top,BorderLayout.NORTH); center.add(receiptScroll(rec),BorderLayout.CENTER);
        p.add(center,BorderLayout.CENTER); return p;
    }
    private String gReceipt(Reservation r){
        return "  BOOKING CONFIRMATION\n  "+DS.LINE+"\n"+
            "  Booking ID   :  #"+r.getId()+"\n  Guest        :  "+r.getGuest()+
            "\n  Contact      :  "+r.getContact()+"\n  Room No.     :  "+r.getRoomNum()+"  ("+r.getRType()+")"+
            "\n  Check-In     :  "+r.getCheckIn()+"\n  Check-Out    :  "+r.getCheckOut()+
            "\n  Nights       :  "+r.getNights()+"\n  Total Amount :  $"+String.format("%.2f",r.getTotal())+
            "\n  Payment      :  PENDING\n  Status       :  "+r.getStatus()+
            "\n  "+DS.LINE+"\n  Keep your Booking ID safe for future reference.";
    }

    private JPanel myBookingsPanel(){
        JPanel p=page(); p.add(new PageHeader("My Bookings","View and cancel your reservations"),BorderLayout.NORTH);
        Card toolbar=new Card(); toolbar.setLayout(new FlowLayout(FlowLayout.LEFT,10,4));
        FlatField nameF=new FlatField("Enter your name",22); nameF.setPreferredSize(new Dimension(220,38));
        RoundBtn find=new RoundBtn("Find Bookings",DS.CHARCOAL,new Color(0x111111),Color.WHITE);
        RoundBtn cancel=new RoundBtn("Cancel Selected",DS.RED,DS.RED.darker(),Color.WHITE);
        toolbar.add(fl("Guest Name:")); toolbar.add(nameF); toolbar.add(find); toolbar.add(cancel);
        DefaultTableModel model=tableModel("ID","Guest","Room","Type","Check-In","Check-Out","Nights","Total","Paid","Status");
        JTable table=TF.make(model);
        find.addActionListener(e->{ String n=nameF.getText().trim(); if(n.isEmpty()){ err("Enter a guest name."); return; }
            List<Reservation> list=svc.byGuest(n); model.setRowCount(0);
            list.forEach(r->model.addRow(new Object[]{"#"+r.getId(),r.getGuest(),String.valueOf(r.getRoomNum()),r.getRType().name(),
                r.getCheckIn().toString(),r.getCheckOut().toString(),String.valueOf(r.getNights()),
                "$"+String.format("%.0f",r.getTotal()),r.isPaid()?"PAID":"UNPAID",r.getStatus().name()}));
            if(list.isEmpty()) inf("No bookings found for '"+n+"'.");
        });
        cancel.addActionListener(e->{ int row=table.getSelectedRow(); if(row<0){ err("Select a booking row to cancel."); return; }
            int id=Integer.parseInt(model.getValueAt(row,0).toString().replace("#",""));
            if(JOptionPane.showConfirmDialog(this,"Cancel booking #"+id+"?","Confirm",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){
                if(svc.cancel(id)){ model.setValueAt("CANCELLED",row,9); ok("Booking #"+id+" has been cancelled."); }
                else err("Unable to cancel — booking may already be cancelled.");
            }
        });
        JPanel center=new JPanel(new BorderLayout(0,12)); center.setOpaque(false);
        center.add(toolbar,BorderLayout.NORTH); center.add(TF.scroll(table),BorderLayout.CENTER);
        p.add(center,BorderLayout.CENTER); return p;
    }

    private JPanel payPanel(){
        JPanel p=page(); p.add(new PageHeader("Process Payment","Simulated card payment — no real charge"),BorderLayout.NORTH);
        Card form=new Card(); form.setLayout(new GridLayout(0,2,14,12));
        FlatField idF=new FlatField("e.g. 2001",10),cardF=new FlatField("16-digit card number",20),
            holderF=new FlatField("Name on card",20),expF=new FlatField("MM/YY",6),cvvF=new FlatField("3 digits",4);
        form.add(fl("Booking ID")); form.add(idF); form.add(fl("Card Number")); form.add(cardF);
        form.add(fl("Card Holder")); form.add(holderF); form.add(fl("Expiry Date")); form.add(expF); form.add(fl("CVV")); form.add(cvvF);
        RoundBtn btn=new RoundBtn("Pay Now",DS.GREEN,DS.GREEN.darker(),Color.WHITE); btn.setMaximumSize(new Dimension(180,40));
        JLabel note=new JLabel("This is a simulated payment. No real transaction occurs."); note.setFont(DS.SMALL); note.setForeground(DS.MUTED);
        btn.addActionListener(e->{ try{
            if(idF.getText().trim().isEmpty()){ err("Enter a Booking ID."); return; }
            if(cardF.getText().trim().isEmpty()){ err("Enter a card number."); return; }
            int id=Integer.parseInt(idF.getText().trim());
            if(svc.pay(id,cardF.getText().trim())){
                ok("Payment successful! Booking #"+id+" is now PAID.");
                idF.setText(""); cardF.setText(""); holderF.setText(""); expF.setText(""); cvvF.setText("");
            }else err("Booking #"+id+" not found, is cancelled, or already paid.");
        }catch(NumberFormatException ex){ err("Enter a valid numeric Booking ID."); }
         catch(Exception ex){ err(ex.getMessage()); } });
        JPanel inner=new JPanel(new BorderLayout(0,10)); inner.setOpaque(false);
        inner.add(form,BorderLayout.NORTH);
        inner.add(new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)){{setOpaque(false);add(btn);}},BorderLayout.CENTER);
        inner.add(note,BorderLayout.SOUTH);
        p.add(inner,BorderLayout.NORTH); return p;
    }
}

// ══════════════════════════════════════════════════════════════
//  ADMIN DASHBOARD
// ══════════════════════════════════════════════════════════════

class AdminDashboard extends Base {
    private static final Color AC=new Color(0x7EB8E8);
    AdminDashboard(HotelService svc){
        super(svc,"The Elite Hotel — Admin",1100,700);
        main=new JPanel(); cl=new CardLayout(); main.setLayout(cl); main.setBackground(DS.BG);
        main.add(dashboardPanel(),"dashboard"); main.add(allBookingsPanel(),"allbookings");
        main.add(roomsPanel(),"rooms"); main.add(manualBookPanel(),"manualbook");
        JPanel root=new JPanel(new BorderLayout());
        root.add(sidebar(DS.SA1,DS.SA2,AC,"Admin Portal",sb->{
            sb.add(navBtn("Dashboard","dashboard",AC));
            sb.add(navBtn("All Reservations","allbookings",AC));
            sb.add(navBtn("Room Inventory","rooms",AC));
            sb.add(navBtn("Manual Booking","manualbook",AC));
            sb.add(Box.createVerticalGlue()); sb.add(logoutBtn()); sb.add(Box.createVerticalStrut(16));
        }),BorderLayout.WEST);
        root.add(main,BorderLayout.CENTER); setContentPane(root); cl.show(main,"dashboard");
    }

    private JPanel dashboardPanel(){
        JPanel p=page(); p.add(new PageHeader("Dashboard","Hotel overview at a glance"),BorderLayout.NORTH);
        List<Reservation> all=svc.allReservations();
        long conf=all.stream().filter(r->r.getStatus()==Reservation.Status.CONFIRMED).count();
        long canc=all.stream().filter(r->r.getStatus()==Reservation.Status.CANCELLED).count();
        long paid=all.stream().filter(Reservation::isPaid).count();
        double rev=all.stream().filter(Reservation::isPaid).mapToDouble(Reservation::getTotal).sum();
        JPanel stats=new JPanel(new GridLayout(1,5,14,0)); stats.setOpaque(false);
        stats.add(new StatCard("Total Rooms",String.valueOf(svc.allRooms().size()),DS.MUTED));
        stats.add(new StatCard("Confirmed",String.valueOf(conf),DS.GREEN));
        stats.add(new StatCard("Cancelled",String.valueOf(canc),DS.RED));
        stats.add(new StatCard("Paid Bookings",String.valueOf(paid),DS.BLUE));
        stats.add(new StatCard("Revenue","$"+String.format("%.0f",rev),DS.GOLD));
        DefaultTableModel model=tableModel("ID","Guest","Room","Check-In","Check-Out","Total","Paid","Status");
        JTable table=TF.make(model);
        List<Reservation> list=new ArrayList<>(all); Collections.reverse(list);
        list.subList(0,Math.min(list.size(),20)).forEach(r->model.addRow(new Object[]{"#"+r.getId(),r.getGuest(),
            r.getRoomNum()+" ("+r.getRType()+")",r.getCheckIn().toString(),r.getCheckOut().toString(),
            "$"+String.format("%.0f",r.getTotal()),r.isPaid()?"PAID":"UNPAID",r.getStatus().name()}));
        JLabel recent=new JLabel("Recent Reservations"); recent.setFont(DS.HEADING); recent.setForeground(DS.CHARCOAL);
        JPanel center=new JPanel(new BorderLayout(0,14)); center.setOpaque(false); center.add(stats,BorderLayout.NORTH);
        JPanel lower=new JPanel(new BorderLayout(0,8)); lower.setOpaque(false);
        lower.add(recent,BorderLayout.NORTH); lower.add(TF.scroll(table),BorderLayout.CENTER);
        center.add(lower,BorderLayout.CENTER); p.add(center,BorderLayout.CENTER); return p;
    }

    private JPanel allBookingsPanel(){
        JPanel p=page(); p.add(new PageHeader("All Reservations","Complete booking records"),BorderLayout.NORTH);
        Card toolbar=new Card(); toolbar.setLayout(new FlowLayout(FlowLayout.LEFT,10,4));
        RoundBtn refresh=new RoundBtn("Refresh",DS.BLUE,DS.BLUE_H,Color.WHITE);
        RoundBtn cancel=new RoundBtn("Cancel Selected",DS.RED,DS.RED.darker(),Color.WHITE);
        RoundBtn clear=new RoundBtn("Clear All Records",new Color(0x8B0000),new Color(0x600000),Color.WHITE);
        toolbar.add(refresh); toolbar.add(cancel); toolbar.add(clear);
        DefaultTableModel model=tableModel("ID","Guest","Contact","Room","Type","Check-In","Check-Out","Nights","Total","Paid","Status");
        JTable table=TF.make(model);
        Runnable load=()->{
            model.setRowCount(0); List<Reservation> list=new ArrayList<>(svc.allReservations()); Collections.reverse(list);
            list.forEach(r->model.addRow(new Object[]{"#"+r.getId(),r.getGuest(),r.getContact(),String.valueOf(r.getRoomNum()),
                r.getRType().name(),r.getCheckIn().toString(),r.getCheckOut().toString(),String.valueOf(r.getNights()),
                "$"+String.format("%.0f",r.getTotal()),r.isPaid()?"PAID":"UNPAID",r.getStatus().name()}));
        };
        load.run();
        refresh.addActionListener(e->load.run());
        cancel.addActionListener(e->{ int row=table.getSelectedRow(); if(row<0){ err("Select a row to cancel."); return; }
            int id=Integer.parseInt(model.getValueAt(row,0).toString().replace("#",""));
            if(JOptionPane.showConfirmDialog(this,"Cancel booking #"+id+"?","Confirm",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){
                if(svc.cancel(id)){ load.run(); ok("Booking #"+id+" cancelled."); } else err("Could not cancel.");
            }
        });
        clear.addActionListener(e->{
            if(JOptionPane.showConfirmDialog(this,
                "<html>This will <b>permanently delete all booking records</b>.<br>This action cannot be undone. Continue?</html>",
                "Clear All Records",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION){
                svc.clearAllRecords(); load.run(); ok("All booking records have been cleared.");
            }
        });
        JPanel center=new JPanel(new BorderLayout(0,12)); center.setOpaque(false);
        center.add(toolbar,BorderLayout.NORTH); center.add(TF.scroll(table),BorderLayout.CENTER);
        p.add(center,BorderLayout.CENTER); return p;
    }

    private JPanel roomsPanel(){
        JPanel p=page(); p.add(new PageHeader("Room Inventory","All rooms with availability check"),BorderLayout.NORTH);
        Card toolbar=new Card(); toolbar.setLayout(new FlowLayout(FlowLayout.LEFT,10,4));
        FlatField ci=new FlatField("yyyy-MM-dd",12); ci.setText(LocalDate.now().format(DS.FMT)); ci.setPreferredSize(new Dimension(130,38));
        FlatField co=new FlatField("yyyy-MM-dd",12); co.setText(LocalDate.now().plusDays(1).format(DS.FMT)); co.setPreferredSize(new Dimension(130,38));
        RoundBtn chk=new RoundBtn("Check Dates",DS.BLUE,DS.BLUE_H,Color.WHITE);
        RoundBtn all=new RoundBtn("Show All",DS.CHARCOAL,new Color(0x111111),Color.WHITE);
        toolbar.add(fl("Check-In:")); toolbar.add(ci); toolbar.add(fl("Check-Out:")); toolbar.add(co); toolbar.add(chk); toolbar.add(all);
        DefaultTableModel model=tableModel("Room No.","Type","Price/Night","Description","Availability");
        JTable table=TF.make(model);
        Runnable showAll=()->{
            model.setRowCount(0);
            svc.allRooms().forEach(r->model.addRow(new Object[]{String.valueOf(r.getNumber()),r.getType().name(),"$"+(int)r.getPrice(),r.getType().getDesc(),"—"}));
        };
        showAll.run();
        chk.addActionListener(e->{ try{
            LocalDate cin=LocalDate.parse(ci.getText().trim(),DS.FMT),cout=LocalDate.parse(co.getText().trim(),DS.FMT);
            if(!cout.isAfter(cin)){ err("Check-out must be after check-in."); return; }
            Set<Integer> avail=svc.searchAvailable(null,cin,cout).stream().map(Room::getNumber).collect(Collectors.toSet());
            model.setRowCount(0);
            svc.allRooms().forEach(r->model.addRow(new Object[]{String.valueOf(r.getNumber()),r.getType().name(),"$"+(int)r.getPrice(),
                r.getType().getDesc(),avail.contains(r.getNumber())?"AVAILABLE":"OCCUPIED"}));
        }catch(Exception ex){ err("Invalid date format."); } });
        all.addActionListener(e->showAll.run());
        JPanel center=new JPanel(new BorderLayout(0,12)); center.setOpaque(false);
        center.add(toolbar,BorderLayout.NORTH); center.add(TF.scroll(table),BorderLayout.CENTER);
        p.add(center,BorderLayout.CENTER); return p;
    }

    private JPanel manualBookPanel(){
        JPanel p=page(); p.add(new PageHeader("Manual Booking","Create a reservation on behalf of a guest"),BorderLayout.NORTH);
        Card form=new Card(); form.setLayout(new GridLayout(0,2,14,12));
        FlatField name=new FlatField("Guest full name",20),contact=new FlatField("Email or phone",20),
            room=new FlatField("Room number",6),
            ci=new FlatField("yyyy-MM-dd",12),co=new FlatField("yyyy-MM-dd",12);
        ci.setText(LocalDate.now().format(DS.FMT)); co.setText(LocalDate.now().plusDays(2).format(DS.FMT));
        JCheckBox payNow=new JCheckBox("Mark as Paid immediately"); payNow.setFont(DS.BODY); payNow.setOpaque(false); payNow.setForeground(DS.TEXT);
        form.add(fl("Guest Name")); form.add(name); form.add(fl("Contact")); form.add(contact);
        form.add(fl("Room Number")); form.add(room); form.add(fl("Check-In")); form.add(ci); form.add(fl("Check-Out")); form.add(co);
        form.add(new JLabel("")); form.add(payNow);
        RoundBtn btn=new RoundBtn("Create Booking",DS.BLUE,DS.BLUE_H,Color.WHITE); btn.setMaximumSize(new Dimension(200,40));
        JTextArea rec=receipt(); rec.setText("  Booking summary will appear here.");
        btn.addActionListener(e->{ try{
            String n=name.getText().trim(),cnt=contact.getText().trim(),rm=room.getText().trim();
            if(n.isEmpty()||cnt.isEmpty()||rm.isEmpty()){ err("Fill in all fields."); return; }
            LocalDate cin=LocalDate.parse(ci.getText().trim(),DS.FMT),cout=LocalDate.parse(co.getText().trim(),DS.FMT);
            if(!cout.isAfter(cin)){ err("Check-out must be after check-in."); return; }
            Reservation res=svc.book(n,cnt,Integer.parseInt(rm),cin,cout);
            if(payNow.isSelected()) svc.pay(res.getId(),"1234567890123456");
            rec.setText(aReceipt(res,payNow.isSelected())); ok("Booking #"+res.getId()+" created.");
        }catch(NumberFormatException ex){ err("Enter a valid room number."); }
         catch(Exception ex){ err(ex.getMessage()); } });
        JPanel btnRow=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)); btnRow.setOpaque(false); btnRow.add(btn);
        JPanel top=new JPanel(new BorderLayout(0,10)); top.setOpaque(false); top.add(form,BorderLayout.NORTH); top.add(btnRow,BorderLayout.SOUTH);
        JPanel center=new JPanel(new BorderLayout(0,14)); center.setOpaque(false);
        center.add(top,BorderLayout.NORTH); center.add(receiptScroll(rec),BorderLayout.CENTER);
        p.add(center,BorderLayout.CENTER); return p;
    }
    private String aReceipt(Reservation r,boolean paid){
        return "  [ADMIN] BOOKING RECORD\n  "+DS.LINE+"\n"+
            "  Booking ID  :  #"+r.getId()+"\n  Guest       :  "+r.getGuest()+
            "\n  Contact     :  "+r.getContact()+"\n  Room        :  "+r.getRoomNum()+"  ("+r.getRType()+")"+
            "\n  Dates       :  "+r.getCheckIn()+"  →  "+r.getCheckOut()+
            "\n  Nights      :  "+r.getNights()+"\n  Total       :  $"+String.format("%.2f",r.getTotal())+
            "\n  Payment     :  "+(paid?"PAID (Admin override)":"PENDING")+
            "\n  Status      :  "+r.getStatus().name()+"\n  "+DS.LINE;
    }
}

// ══════════════════════════════════════════════════════════════
//  MAIN
// ══════════════════════════════════════════════════════════════

public class Task2 {
    public static void main(String[] args){
        try{ UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }catch(Exception ignored){}
        SwingUtilities.invokeLater(()->{ HotelService svc=new HotelService(); new LoginScreen(svc).setVisible(true); });
    }
}