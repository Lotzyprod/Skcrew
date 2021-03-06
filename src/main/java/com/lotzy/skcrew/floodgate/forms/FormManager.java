package com.lotzy.skcrew.floodgate.forms;

import org.bukkit.event.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

public class FormManager {

    private final List<Form> forms = new ArrayList<>();
    private final WeakHashMap<Event, Form> eventForms = new WeakHashMap<>();


    public void register(Form form) {
        forms.add(form);
    }

    public void unregister(Form form) {
        forms.remove(form);
        eventForms.values().removeIf(eventForm -> eventForm == form);
    }

    public List<Form> getTrackedForms() {
        return forms;
    }

    @Nullable
    public Form getForm(Event event) {
        return eventForms.get(event);
    }

    public void setForm(Event event, @Nullable Form form) {
        if (form != null) {
            eventForms.put(event, form);
        } else {
            eventForms.remove(event);
        }
    }

    @Nullable
    public Form getForm(String id) {
        for (Form form : forms) {
            if (form.getID() != null && form.getID().equals(id)) {
                return form;
            }
        }
        return null;
    }

}