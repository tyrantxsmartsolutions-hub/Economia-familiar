package com.economiafamiliar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    // ---------- Colores ----------
    static final int BLUE = Color.rgb(21, 101, 192);
    static final int GREEN = Color.rgb(46, 125, 50);
    static final int RED = Color.rgb(198, 40, 40);
    static final int YELLOW = Color.rgb(230, 145, 0);
    static final int BG = Color.rgb(244, 246, 250);
    static final int TEXT = Color.rgb(33, 37, 41);
    static final int MUTED = Color.rgb(108, 117, 125);
    static final int LINE = Color.rgb(228, 231, 236);

    static final String[] EXPENSE_CATS = {
            "Alimentos", "Supermercado", "Transporte", "Servicios", "Vivienda", "Salud",
            "Educación", "Ropa", "Entretenimiento", "Deudas", "Ahorro", "Otros"
    };
    static final String[] INCOME_CATS = {"Sueldo", "Ingreso extra", "Otro ingreso"};
    static final int[] PALETTE = {
            Color.rgb(21, 101, 192), Color.rgb(0, 150, 136), Color.rgb(249, 168, 37),
            Color.rgb(142, 36, 170), Color.rgb(244, 81, 30), Color.rgb(67, 160, 71)
    };

    // ---------- Estado ----------
    SharedPreferences sp;
    JSONArray tx = new JSONArray();
    final Calendar viewMonth = Calendar.getInstance();
    int tab = 0; // 0 = Resumen, 1 = Movimientos, 2 = Presupuesto

    LinearLayout content;
    TextView monthLabel;
    final TextView[] tabViews = new TextView[3];

    static class Summary {
        double income;
        double expense;
        Map<String, Double> byCat = new HashMap<>();
    }

    // =====================================================================
    //  Ciclo de vida
    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("data", MODE_PRIVATE);
        try {
            tx = new JSONArray(sp.getString("tx", "[]"));
        } catch (JSONException e) {
            tx = new JSONArray();
        }
        viewMonth.set(Calendar.DAY_OF_MONTH, 1);
        build();
        render();
    }

    // =====================================================================
    //  Estructura principal de la pantalla
    // =====================================================================
    void build() {
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // ----- Encabezado azul con selector de mes -----
        final LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(BLUE);
        final int headerTop = dp(12);
        header.setPadding(dp(8), headerTop, dp(8), dp(8));

        TextView title = text("Economía Familiar", 20, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, lp(-1, -2));

        LinearLayout monthRow = new LinearLayout(this);
        monthRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("‹", 30, Color.WHITE, true);
        prev.setPadding(dp(20), 0, dp(20), 0);
        prev.setOnClickListener(v -> {
            viewMonth.add(Calendar.MONTH, -1);
            render();
        });
        TextView next = text("›", 30, Color.WHITE, true);
        next.setPadding(dp(20), 0, dp(20), 0);
        next.setOnClickListener(v -> {
            viewMonth.add(Calendar.MONTH, 1);
            render();
        });
        monthLabel = text("", 16, Color.WHITE, false);
        monthLabel.setGravity(Gravity.CENTER);
        monthRow.addView(prev, lp(-2, -2));
        monthRow.addView(monthLabel, lp(0, -2, 1f));
        monthRow.addView(next, lp(-2, -2));
        header.addView(monthRow, lp(-1, -2));
        root.addView(header, lp(-1, -2));

        // ----- Contenido que se desplaza -----
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content, lp(-1, -2));
        root.addView(scroll, lp(-1, 0, 1f));

        // ----- Barra inferior -----
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        root.addView(divider, lp(-1, dp(1)));

        final LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        String[] labels = {"Resumen", "Movimientos", "Presupuesto"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = text(labels[i], 14, MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(16), 0, dp(16));
            t.setOnClickListener(v -> {
                tab = index;
                render();
            });
            tabViews[i] = t;
            nav.addView(t, lp(0, -2, 1f));
        }
        root.addView(nav, lp(-1, -2));

        // ----- Android 15/16 dibuja la app debajo de las barras del sistema:
        //       hay que dejar espacio arriba (estado) y abajo (navegación / teclado) -----
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            int ime = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
                ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            boolean keyboard = ime > bottom;
            header.setPadding(dp(8), headerTop + top, dp(8), dp(8));
            nav.setPadding(0, 0, 0, bottom);
            nav.setVisibility(keyboard ? View.GONE : View.VISIBLE);
            root.setPadding(0, 0, 0, keyboard ? ime : 0);
            return insets;
        });

        setContentView(root);
        root.requestApplyInsets();
        setupSystemBars();
    }

    void setupSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT < 35) {
            w.setStatusBarColor(BLUE);
            w.setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? Color.WHITE : Color.BLACK);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                // iconos claros arriba (fondo azul) y oscuros abajo (barra blanca)
                c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                c.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= 26) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    void render() {
        monthLabel.setText(monthTitle());
        for (int i = 0; i < tabViews.length; i++) {
            tabViews[i].setTextColor(i == tab ? BLUE : MUTED);
        }
        content.removeAllViews();
        if (tab == 0) {
            renderHome();
        } else if (tab == 1) {
            renderHistory();
        } else {
            renderBudget();
        }
    }

    // =====================================================================
    //  Pantallas
    // =====================================================================
    void renderHome() {
        Summary s = summary();
        double balance = s.income - s.expense;
        double budget = budget();

        // --- Saldo del mes ---
        LinearLayout c1 = card();
        c1.addView(text("Disponible del mes", 14, MUTED, false));
        c1.addView(text(money(balance), 32, balance < 0 ? RED : GREEN, true));
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(8), 0, 0);
        r.addView(column("Ingresos", money(s.income), GREEN), lp(0, -2, 1f));
        r.addView(column("Gastos", money(s.expense), RED), lp(0, -2, 1f));
        c1.addView(r, lp(-1, -2));

        // --- Botones de acción ---
        LinearLayout buttons = new LinearLayout(this);
        Button addExpense = primary("+ Gasto", RED);
        Button addIncome = primary("+ Ingreso", GREEN);
        addExpense.setOnClickListener(v -> showAddDialog(false));
        addIncome.setOnClickListener(v -> showAddDialog(true));
        LinearLayout.LayoutParams left = lp(0, -2, 1f);
        left.rightMargin = dp(6);
        LinearLayout.LayoutParams right = lp(0, -2, 1f);
        right.leftMargin = dp(6);
        buttons.addView(addExpense, left);
        buttons.addView(addIncome, right);
        LinearLayout.LayoutParams bp = lp(-1, -2);
        bp.bottomMargin = dp(12);
        content.addView(buttons, bp);

        // --- Gasto diario recomendado ---
        LinearLayout c2 = card();
        double base = budget > 0 ? budget - s.expense : balance;
        if (isCurrentMonth()) {
            int daysLeft = daysLeft();
            double daily = base / daysLeft;
            c2.addView(text("Podés gastar por día", 14, MUTED, false));
            c2.addView(text(money(Math.max(0, daily)), 26, TEXT, true));
            String msg;
            int color;
            if (base < 0) {
                msg = "🔴 Te pasaste por " + money(-base) + (budget > 0 ? " del presupuesto" : " de tus ingresos");
                color = RED;
            } else if (budget > 0 && s.expense >= budget * 0.8) {
                msg = "🟡 Cuidado: ya usaste el " + Math.round(s.expense * 100 / budget) + "% del presupuesto";
                color = YELLOW;
            } else {
                msg = "🟢 Vas bien · quedan " + daysLeft + " días del mes";
                color = GREEN;
            }
            c2.addView(text(msg, 15, color, true));
        } else {
            c2.addView(text("El gasto diario solo se calcula para el mes en curso.", 14, MUTED, false));
        }

        // --- Presupuesto ---
        if (budget > 0) {
            LinearLayout c3 = card();
            c3.addView(row("Presupuesto del mes", money(s.expense) + " de " + money(budget), TEXT, MUTED, true));
            double ratio = s.expense / budget;
            int color = ratio >= 1 ? RED : (ratio >= 0.8 ? YELLOW : BLUE);
            LinearLayout.LayoutParams barP = lp(-1, dp(8));
            barP.topMargin = dp(8);
            c3.addView(bar(ratio, color), barP);
        }

        // --- Gastos por categoría ---
        LinearLayout c4 = card();
        c4.addView(text("Gastos por categoría", 16, TEXT, true));
        if (s.byCat.isEmpty()) {
            LinearLayout.LayoutParams p = lp(-2, -2);
            p.topMargin = dp(6);
            c4.addView(text("Todavía no cargaste gastos este mes.", 14, MUTED, false), p);
        } else {
            List<Map.Entry<String, Double>> list = new ArrayList<>(s.byCat.entrySet());
            Collections.sort(list, (a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < list.size(); i++) {
                Map.Entry<String, Double> e = list.get(i);
                LinearLayout.LayoutParams rp = lp(-1, -2);
                rp.topMargin = dp(10);
                c4.addView(row(e.getKey(), money(e.getValue()), TEXT, TEXT, false), rp);
                LinearLayout.LayoutParams bp2 = lp(-1, dp(8));
                bp2.topMargin = dp(4);
                c4.addView(bar(e.getValue() / s.expense, PALETTE[i % PALETTE.length]), bp2);
            }
        }
    }

    void renderHistory() {
        String key = monthKey();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < tx.length(); i++) {
            JSONObject o = tx.optJSONObject(i);
            if (o != null && o.optString("date").startsWith(key)) {
                idx.add(i);
            }
        }
        // más recientes primero
        Collections.sort(idx, (a, b) -> {
            int c = tx.optJSONObject(b).optString("date").compareTo(tx.optJSONObject(a).optString("date"));
            return c != 0 ? c : b - a;
        });

        LinearLayout c = card();
        c.addView(text("Movimientos", 16, TEXT, true));
        if (idx.isEmpty()) {
            LinearLayout.LayoutParams p = lp(-2, -2);
            p.topMargin = dp(6);
            c.addView(text("No hay movimientos en este mes.", 14, MUTED, false), p);
            return;
        }
        c.addView(text("Tocá un movimiento para eliminarlo.", 12, MUTED, false));
        for (final int index : idx) {
            JSONObject o = tx.optJSONObject(index);
            boolean income = o.optBoolean("income");
            String date = o.optString("date");
            String desc = o.optString("desc");
            String shortDate = date.length() >= 10 ? date.substring(8, 10) + "/" + date.substring(5, 7) : date;

            View line = new View(this);
            line.setBackgroundColor(LINE);
            LinearLayout.LayoutParams lineP = lp(-1, dp(1));
            lineP.topMargin = dp(8);
            c.addView(line, lineP);

            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(0, dp(10), 0, dp(2));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(o.optString("cat", "Otros"), 16, TEXT, true));
            info.addView(text(shortDate + (desc.isEmpty() ? "" : " · " + desc), 13, MUTED, false));
            item.addView(info, lp(0, -2, 1f));
            item.addView(text((income ? "+" : "-") + money(o.optDouble("amount", 0)), 16, income ? GREEN : RED, true));
            item.setOnClickListener(v -> confirmDelete(index));
            c.addView(item, lp(-1, -2));
        }
    }

    void renderBudget() {
        LinearLayout c = card();
        c.addView(text("Límite de gastos del mes", 16, TEXT, true));
        c.addView(text("Es opcional. Si lo definís, el gasto diario se calcula sobre este límite.", 13, MUTED, false));

        final EditText e = new EditText(this);
        e.setHint("Ej: 500000");
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(sp.getString("budget", ""));
        c.addView(e, lp(-1, -2));

        Button save = primary("Guardar presupuesto", BLUE);
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(10);
        c.addView(save, p);
        save.setOnClickListener(v -> {
            sp.edit().putString("budget", e.getText().toString().trim()).apply();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(e.getWindowToken(), 0);
            }
            Toast.makeText(this, "Presupuesto guardado", Toast.LENGTH_SHORT).show();
            render();
        });

        double budget = budget();
        if (budget > 0) {
            Summary s = summary();
            LinearLayout c2 = card();
            c2.addView(text("Estado de " + monthTitle(), 14, MUTED, false));
            c2.addView(text("Gastaste " + money(s.expense) + " de " + money(budget)
                    + " (" + Math.round(s.expense * 100 / budget) + "%)", 16, TEXT, true));
            double left = budget - s.expense;
            c2.addView(text(left >= 0 ? "Te quedan " + money(left) : "Te pasaste por " + money(-left),
                    15, left >= 0 ? GREEN : RED, true));
        }
    }

    // =====================================================================
    //  Diálogos
    // =====================================================================
    void showAddDialog(final boolean isIncome) {
        final Calendar cal = Calendar.getInstance();
        if (!isCurrentMonth()) {
            cal.set(viewMonth.get(Calendar.YEAR), viewMonth.get(Calendar.MONTH), 1);
        }

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText amount = new EditText(this);
        amount.setHint("Monto");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        l.addView(amount, lp(-1, -2));

        final Spinner cat = new Spinner(this);
        cat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                isIncome ? INCOME_CATS : EXPENSE_CATS));
        l.addView(cat, lp(-1, dp(48)));

        final EditText desc = new EditText(this);
        desc.setHint("Descripción (opcional)");
        desc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        l.addView(desc, lp(-1, -2));

        final SimpleDateFormat shown = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        final Button dateBtn = new Button(this);
        dateBtn.setAllCaps(false);
        dateBtn.setText("Fecha: " + shown.format(cal.getTime()));
        dateBtn.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            cal.set(y, m, d);
            dateBtn.setText("Fecha: " + shown.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());
        l.addView(dateBtn, lp(-1, -2));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isIncome ? "Nuevo ingreso" : "Nuevo gasto")
                .setView(l)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            double value;
            try {
                value = Double.parseDouble(amount.getText().toString().trim().replace(",", "."));
            } catch (NumberFormatException ex) {
                value = 0;
            }
            if (value <= 0) {
                Toast.makeText(this, "Ingresá un monto válido", Toast.LENGTH_SHORT).show();
                return;
            }
            saveTransaction(isIncome, value, cat.getSelectedItem().toString(),
                    desc.getText().toString().trim(), cal);
            dialog.dismiss();
        }));
        dialog.show();
    }

    void saveTransaction(boolean income, double amount, String cat, String desc, Calendar cal) {
        try {
            JSONObject o = new JSONObject();
            o.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime()));
            o.put("income", income);
            o.put("amount", amount);
            o.put("cat", cat);
            o.put("desc", desc);
            tx.put(o);
            persist();
            // mostrar el mes del movimiento recién cargado
            viewMonth.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1);
            render();
        } catch (JSONException e) {
            Toast.makeText(this, "No se pudo guardar", Toast.LENGTH_SHORT).show();
        }
    }

    void confirmDelete(final int index) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar movimiento")
                .setMessage("¿Querés eliminar este movimiento?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> {
                    tx.remove(index);
                    persist();
                    render();
                })
                .show();
    }

    // =====================================================================
    //  Datos
    // =====================================================================
    void persist() {
        sp.edit().putString("tx", tx.toString()).apply();
    }

    Summary summary() {
        Summary s = new Summary();
        String key = monthKey();
        for (int i = 0; i < tx.length(); i++) {
            JSONObject o = tx.optJSONObject(i);
            if (o == null || !o.optString("date").startsWith(key)) {
                continue;
            }
            double a = o.optDouble("amount", 0);
            if (o.optBoolean("income")) {
                s.income += a;
            } else {
                s.expense += a;
                String c = o.optString("cat", "Otros");
                Double prev = s.byCat.get(c);
                s.byCat.put(c, (prev == null ? 0 : prev) + a);
            }
        }
        return s;
    }

    double budget() {
        try {
            return Double.parseDouble(sp.getString("budget", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    String monthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(viewMonth.getTime());
    }

    String monthTitle() {
        String s = new SimpleDateFormat("MMMM yyyy", new Locale("es")).format(viewMonth.getTime());
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    boolean isCurrentMonth() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) == viewMonth.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == viewMonth.get(Calendar.MONTH);
    }

    int daysLeft() {
        Calendar c = Calendar.getInstance();
        return c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH) + 1;
    }

    String money(double x) {
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        return (x < 0 ? "-" : "") + "$" + f.format(Math.abs(x));
    }

    // =====================================================================
    //  Ayudas de interfaz
    // =====================================================================
    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Tarjeta blanca ya agregada al contenido. */
    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(Color.WHITE, 16));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setElevation(dp(2));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.bottomMargin = dp(12);
        content.addView(c, p);
        return c;
    }

    Button primary(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(color, 12));
        b.setMinimumHeight(dp(48));
        return b;
    }

    LinearLayout column(String label, String value, int valueColor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(text(label, 13, MUTED, false));
        c.addView(text(value, 18, valueColor, true));
        return c;
    }

    LinearLayout row(String left, String right, int leftColor, int rightColor, boolean boldLeft) {
        LinearLayout r = new LinearLayout(this);
        r.addView(text(left, 15, leftColor, boldLeft), lp(0, -2, 1f));
        r.addView(text(right, 15, rightColor, false), lp(-2, -2));
        return r;
    }

    /** Barra de progreso simple (0.0 a 1.0). */
    LinearLayout bar(double fraction, int color) {
        float f = (float) Math.max(0.0, Math.min(1.0, fraction));
        LinearLayout track = new LinearLayout(this);
        track.setBackground(rounded(Color.rgb(230, 233, 238), 4));
        View fill = new View(this);
        fill.setBackground(rounded(color, 4));
        track.addView(fill, lp(0, -1, f));
        track.addView(new View(this), lp(0, -1, 1f - f));
        return track;
    }
}
