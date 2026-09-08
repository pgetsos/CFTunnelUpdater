package gr.pgetsos.cftunnelupdater;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class IPAdapter extends RecyclerView.Adapter<IPAdapter.ViewHolder> {

    private List<String> ipList;
    private Map<String, String> names = new HashMap<>();
    private OnIpLongPressListener clickListener;
    private OnIpLongPressListener longPressListener;

    public IPAdapter(List<String> ipList, OnIpLongPressListener longPressListener) {
        this.ipList = ipList;
        this.longPressListener = longPressListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String ip = ipList.get(position);
        String name = names.get(IpNameKey.of(ip));
        holder.ipTextView.setText(name == null || name.isEmpty() ? ip : name + "\n" + ip);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onIpLongPressed(ip, holder.getAdapterPosition());
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longPressListener != null) {
                longPressListener.onIpLongPressed(ip, position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return ipList.size();
    }

    public void setClickListener(OnIpLongPressListener listener) { clickListener = listener; }

    public void updateNames(Map<String, String> newNames) {
        names = new HashMap<>(newNames);
        notifyDataSetChanged();
    }

    public void updateList(List<String> newList) {
        ipList.clear();
        ipList.addAll(newList);
        notifyDataSetChanged(); // DiffUtil todo
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView ipTextView;

        ViewHolder(View itemView) {
            super(itemView);
            ipTextView = itemView.findViewById(R.id.ip_text);
        }
    }
}
