package gr.pgetsos.cftunnelupdater;
import android.app.*;
import android.content.Context;
import java.util.*;
import java.text.DateFormat;
import java.util.function.Consumer;
public final class ExpiryPicker {
    public static String label(Long value) { return value == null ? "No expiry" : "Expires: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(value)); }
    public static void show(Context context, Long existing, Consumer<Long> callback) {
        new AlertDialog.Builder(context).setTitle("IP expiry")
            .setItems(new String[]{"15 minutes", "1 hour", "1 day", "1 month", "Custom", "Never"}, (dialog, option) -> {
                if (option == 5) { callback.accept(null); return; }
                if (option < 4) {
                    Calendar preset = Calendar.getInstance();
                    if (option == 0) preset.add(Calendar.MINUTE, 15);
                    else if (option == 1) preset.add(Calendar.HOUR_OF_DAY, 1);
                    else if (option == 2) preset.add(Calendar.DAY_OF_MONTH, 1);
                    else preset.add(Calendar.MONTH, 1);
                    callback.accept(preset.getTimeInMillis());
                    return;
                }
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(existing == null ? System.currentTimeMillis() + 3600000 : existing);
                DatePickerDialog date = new DatePickerDialog(context, (picker,y,m,d) -> {
                    c.set(y,m,d);
                    new TimePickerDialog(context, (time,h,min) -> {
                        c.set(Calendar.HOUR_OF_DAY,h); c.set(Calendar.MINUTE,min); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
                        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
                            android.widget.Toast.makeText(context,"Choose a future expiry time.",android.widget.Toast.LENGTH_LONG).show();
                        } else callback.accept(c.getTimeInMillis());
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show();
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
                date.getDatePicker().setMinDate(System.currentTimeMillis()); date.show();
            }).show();
    }
}
