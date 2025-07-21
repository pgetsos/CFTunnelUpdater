package gr.pgetsos.cftunnelupdater;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class IPAdapter extends RecyclerView.Adapter<IPAdapter.ViewHolder> {

    private List<String> ipList;
    private OnIpLongPressListener longPressListener;

    // Modify constructor to accept the listener
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
        holder.ipTextView.setText(ip);

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
