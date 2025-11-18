// src/main/java/com/ayb/busticketpos/TicketPagerAdapter.java
package com.ayb.busticketpos;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;

public class TicketPagerAdapter extends FragmentStateAdapter {
    private final StageViewModel vm;

    public TicketPagerAdapter(
            @NonNull FragmentActivity fa,
            @NonNull StageViewModel vm
    ) {
        super(fa);
        this.vm = vm;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return Adult_Ticket_Fragment.newInstance(vm);
            case 1:
                return Student_Ticket_Fragment.newInstance(vm);
            case 2:
                return Children_Ticket_Fragment.newInstance(vm);
            default:
                throw new IllegalArgumentException("Unexpected position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 3;  // Adult, Student, Children
    }
}
