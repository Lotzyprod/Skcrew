package com.lotzy.skcrew.floodgate.forms.events;

import com.lotzy.skcrew.floodgate.forms.Form;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.CustomForm;
import org.geysermc.cumulus.ModalForm;
import org.geysermc.cumulus.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.ModalFormResponse;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.cumulus.util.FormBuilder;

public class FormListener {
    public static FormBuilder FormListener(Player p, Form raw) {
        Bukkit.getPluginManager().callEvent(new FormOpenEvent(p, raw));
        FormBuilder res;
        if(raw.getForm().get() instanceof ModalForm.Builder) {
            res = ((ModalForm.Builder)raw.getForm().get()).responseHandler((form, r) -> {
            
                ModalFormResponse response = form.parseResponse(r);
                
                if (response.isCorrect())
                    Bukkit.getPluginManager().callEvent(new FormSubmitEvent(p, raw, response));
                Bukkit.getPluginManager().callEvent(new FormCloseEvent(p, raw));
            });
        } else if (raw.getForm().get() instanceof SimpleForm.Builder) {
            res = ((SimpleForm.Builder)raw.getForm().get()).responseHandler((form, r) -> {
            
                SimpleFormResponse response = form.parseResponse(r);
                
                if (response.isCorrect())
                    Bukkit.getPluginManager().callEvent(new FormSubmitEvent(p, raw, response));
                Bukkit.getPluginManager().callEvent(new FormCloseEvent(p, raw));
            });
        } else {
            res = ((CustomForm.Builder)raw.getForm().get()).responseHandler((form, r) -> {
            
                CustomFormResponse response = form.parseResponse(r);
                if (response.isCorrect() || !response.isClosed())
                    Bukkit.getPluginManager().callEvent(new FormSubmitEvent(p, raw, response)); 
                Bukkit.getPluginManager().callEvent(new FormCloseEvent(p, raw));
            });
        }
        
        return res;
    }
}
