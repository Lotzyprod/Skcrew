package com.lotzy.skcrew.skriptgui.gui;

import com.lotzy.skcrew.Skcrew;
import com.lotzy.skcrew.skriptgui.SkriptGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class GUI {

	private Inventory inventory;
	private String name;

	private final GUIEventHandler eventHandler = new GUIEventHandler() {
		@Override
		public void onClick(InventoryClickEvent e) {
			if (isPaused() || isPaused((Player) e.getWhoClicked())) {
				e.setCancelled(true); // Just in case
				return;
			}

			Character realSlot = convert(e.getSlot());
			Consumer<InventoryClickEvent> run = slots.get(realSlot);
			/*
			 * Cancel the event if this GUI slot is a button (it runs a consumer)
			 * If it isn't, check whether items are stealable in this GUI, or if the specific slot is stealable
			 */
			e.setCancelled(run != null || (!isStealable() && !isStealable(realSlot)));
			if (run != null) {
				SkriptGUI.getGUIManager().setGUI(e, GUI.this);
				run.accept(e);
			}
		}

		@Override
		public void onDrag(InventoryDragEvent e) {
			if (isPaused() || isPaused((Player) e.getWhoClicked())) {
				e.setCancelled(true); // Just in case
				return;
			}

			for (int slot : e.getRawSlots()) {
				Character realSlot = convert(slot);
				/*
				 * Cancel the event if this GUI slot is a button (it runs a consumer)
				 * If it isn't, check whether items are stealable in this GUI, or if the specific slot is stealable
				 */
				e.setCancelled(slots.get(realSlot) != null || (!isStealable() && !isStealable(realSlot)));
				break;
			}
		}

		@Override
		public void onOpen(InventoryOpenEvent e) {
			if (isPaused() || isPaused((Player) e.getPlayer())) {
				return;
			}

			if (onOpen != null) {
				SkriptGUI.getGUIManager().setGUI(e, GUI.this);
				onOpen.accept(e);
			}
		}

		@Override
		public void onClose(InventoryCloseEvent e) {
			if (isPaused() || isPaused((Player) e.getPlayer())) {
				return;
			}

			if (onClose != null) {
				SkriptGUI.getGUIManager().setGUI(e, GUI.this);
				onClose.accept(e);
				if (closeCancelled) {
					Bukkit.getScheduler().runTaskLater(Skcrew.getInstance(), () -> {
						// Reset behavior (it shouldn't persist)
						setCloseCancelled(false);

						Player closer = (Player) e.getPlayer();
						pause(closer); // Avoid calling any open sections
						closer.openInventory(inventory);
						resume(closer);
					}, 1);
					return;
				}
			}

			if (id == null && inventory.getViewers().size() == 1) { // Only stop tracking if it isn't a global GUI
				Bukkit.getScheduler().runTaskLater(Skcrew.getInstance(), () -> SkriptGUI.getGUIManager().unregister(GUI.this), 1);
			}

			// To combat issues like https://github.com/APickledWalrus/skript-gui/issues/60
			Bukkit.getScheduler().runTaskLater(Skcrew.getInstance(), () -> ((Player) e.getPlayer()).updateInventory(), 1);
		}
	};

	private final Map<Character, Consumer<InventoryClickEvent>> slots = new HashMap<>();
	@Nullable
	private String rawShape;

	// Whether all items of this GUI (excluding buttons) can be taken.
	private boolean stealableItems;
	/*
	 * The individual slots of this GUI that can be stolen.
	 * Even if stealableItems is false, slots in this list will be stealable.
	 * Ignored if 'stealableItems' is true.
	 */
	private final List<Character> stealableSlots = new ArrayList<>();

	// To be ran when this inventory is opened.
	@Nullable
	private Consumer<InventoryOpenEvent> onOpen;
	// To be ran when this inventory is closed.
	@Nullable
	private Consumer<InventoryCloseEvent> onClose;
	// Whether the inventory close event for this event handler is cancelled.
	private boolean closeCancelled;

	@Nullable
	private String id;

	public GUI(Inventory inventory, boolean stealableItems, @Nullable String name) {
		this.inventory = inventory;
		this.stealableItems = stealableItems;
		this.name = name != null ? name : inventory.getType().getDefaultTitle();
		SkriptGUI.getGUIManager().register(this);
	}

	public Inventory getInventory() {
		return inventory;
	}

	public GUIEventHandler getEventHandler() {
		return eventHandler;
	}

	public void setSize(int size) {
		changeInventory(size, getName());
	}

	public String getName() {
		return name;
	}

	public void setName(@Nullable String name) {
		changeInventory(inventory.getSize(), name);
	}

	public void clear(Object slot) {
		Character realSlot = convert(slot);
		setItem(realSlot, new ItemStack(Material.AIR), false, null);
		slots.remove(realSlot);
	}

	public void clear() {
		inventory.clear();
		slots.clear();
		stealableSlots.clear();
	}

	private void changeInventory(int size, @Nullable String name) {
		if (name == null) {
			name = inventory.getType().getDefaultTitle();
		} else if (size < 9 ) { // Minimum size
			size = 9;
		} else if (size > 54) { // Maximum size
			size = 54;
		}

		if (size == inventory.getSize() && name.equals(this.name)) { // Nothing is actually changing
			return;
		}

		Inventory newInventory;
		if (inventory.getType() == InventoryType.CHEST) {
			newInventory = Bukkit.getServer().createInventory(null, size, name);
		} else {
			newInventory = Bukkit.getServer().createInventory(null, inventory.getType(), name);
		}

		if (size >= inventory.getSize()) {
			newInventory.setContents(inventory.getContents());
		} else { // The inventory is shrinking
			ItemStack[] oldContents = inventory.getContents();
			ItemStack[] contents = new ItemStack[size];
			for (int slot = 0; slot < size; slot++) {
				contents[slot] = oldContents[slot];
			}
			newInventory.setContents(contents);
		}

		eventHandler.pause(); // Don't process any events as we transfer data and players

		for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
			ItemStack cursor = viewer.getItemOnCursor();
			viewer.setItemOnCursor(null);
			viewer.openInventory(newInventory);
			viewer.setItemOnCursor(cursor);
		}
		inventory = newInventory;
		this.name = name;

		eventHandler.resume(); // It is safe to resume operations
	}

	/**
	 * @param slot The object to convert to Character form
	 * @return A Character that is usable in the item and slot maps.
	 */
	public Character convert(Object slot) {
		if (slot instanceof Character) {
			return (Character) slot;
		}

		if (slot instanceof Number) {
			int invSlot = ((Number) slot).intValue();
			// Make sure inventory slot is at least 0 (see https://github.com/APickledWalrus/skript-gui/issues/48)
			if (rawShape != null && invSlot >= 0 && invSlot < rawShape.length()) {
				return rawShape.charAt(invSlot);
			}
			return ' ';
		}

		if (slot instanceof String && !((String) slot).isEmpty()) {
			char strSlot = ((String) slot).charAt(0);
			return (rawShape != null && rawShape.contains(Character.toString(strSlot))) ? strSlot : ' ';
		}

		return nextSlot();
	}

	/**
	 * @return The next available slot in this GUI.
	 */
	public Character nextSlot() {
		if (rawShape != null) {
			for (char ch : rawShape.toCharArray()) {
				if (!slots.containsKey(ch)) {
					return ch;
				}
			}
		}
		return 0;
	}

	/**
	 * @return The newest slot that has been filled in this GUI.
	 */
	public Character nextSlotInverted() {
		if (rawShape != null) {
			for (char ch : rawShape.toCharArray()) {
				if (slots.containsKey(ch)) {
					return ch;
				}
			}
		}
		return 0;
	}

	/**
	 * Sets a slot's item.
	 * @param slot The slot to put the item in. It will be converted by {@link GUI#convert(Object)}.
	 * @param item The {@link ItemStack} to put in the slot.
	 * @param stealable Whether this {@link ItemStack} can be stolen.
	 * @param consumer The {@link Consumer} that the slot will run when clicked. Put as null if the slot should not run anything when clicked.
	 */
	public void setItem(Object slot, @Nullable ItemStack item, boolean stealable, @Nullable Consumer<InventoryClickEvent> consumer) {
		if (rawShape == null) {
			Skcrew.getInstance().getLogger().warning("Unable to set the item in a gui named '" + getName() + "' as it has a null shape.");
			return;
		}

		Character ch = convert(slot);
		if (ch == ' ') {
			return;
		}
		if (ch == '+' && rawShape.contains("+")) {
			char ch2 = 'A';
			while (rawShape.indexOf(ch2) >= 0) {
				ch2++;
			}
			rawShape = rawShape.replaceFirst("\\+", "" + ch2);
			ch = ch2;
		}

		// Although we may be adding null consumers, it lets us track what slots have been set
		slots.put(ch, consumer);

		if (stealable) {
			stealableSlots.add(ch);
		} else { // Just in case as we may be updating a slot
			stealableSlots.remove(ch);
		}

		int i = 0;
		for (char ch1 : rawShape.toCharArray()) {
			if (ch == ch1 && i < inventory.getSize()) {
				inventory.setItem(i, item);
			}
			i++;
		}
	}

	/**
	 * @param slot The slot to get the item from. It will be converted.
	 * @return The item at this slot, or AIR if the slot has no item, or the slot is not valid for this GUI.
	 */
	public ItemStack getItem(Object slot) {
		if (rawShape == null) {
			return new ItemStack(Material.AIR);
		}
		char ch = convert(slot);
		if (ch == 0) {
			return new ItemStack(Material.AIR);
		}
		ItemStack item = inventory.getItem(rawShape.indexOf(ch));
		return item != null ? item : new ItemStack(Material.AIR);
	}

	/**
	 * @return The raw shape of this GUI. May be null if the shape has not yet been initialized.
	 * @see #setShape(String...) 
	 */
	@Nullable
	public String getRawShape() {
		return rawShape;
	}

	/**
	 * Resets the shape of this {@link GUI}
	 */
	public void resetShape() {
		int size = 54; // Max inventory size

		String[] shape = new String[size / 9];

		int position = 0;
		StringBuilder sb = new StringBuilder();
		for (char c = 'A'; c < size + 'A'; c++) { // Create the default shape in String form.
			sb.append(c);
			if (sb.length() == 9) {
				shape[position] = sb.toString();
				sb = new StringBuilder();
				position++;
			}
		}

		setShape(shape);
	}

	/**
	 * Sets the shape of this {@link GUI}
	 * @param shapes The new shape patterns for this {@link GUI}
	 * @see GUI#getRawShape()
	 */
	public void setShape(String... shapes) {
		if (shapes.length == 0) {
			return;
		}

		int size = inventory.getSize();

		StringBuilder sb = new StringBuilder();
		for (String shape : shapes) {
			sb.append(shape);
		}
		while (sb.length() < size) { // Fill it in if it's too small
			sb.append(' ');
		}

		String newShape = sb.toString();
		Map<Character, ItemStack> movedCharacters = new HashMap<>();

		if (rawShape != null) {
			int pos = 0;
			for (char ch : rawShape.toCharArray()) {
				if (rawShape.indexOf(ch) == pos) { // Only check a character once
					if (newShape.indexOf(ch) == -1) { // This character IS NOT in the new shape
						clear(ch);
					} else { // This character IS in the new shape
						movedCharacters.put(ch, getItem(ch));
					}
				}
				pos++;
			}
		}

		// Clear out the slots of characters that are new to the shape (just in case they were occupied before)
		// We only need to clear the slot of the item as actions (clicking, stealing, etc.) will already have been changed
		if (rawShape != null) {
			for (int i = 0; i < inventory.getSize(); i++) {
				if (rawShape.indexOf(newShape.charAt(i)) == -1) { // This character was NOT in the old shape
					inventory.clear(i);
				}
			}
		}

		rawShape = newShape;

		// Move around items for the moved characters
		for (Entry<Character, ItemStack> movedCharacter : movedCharacters.entrySet()) {
			Character ch = movedCharacter.getKey();
			setItem(ch, movedCharacter.getValue(), isStealable(ch), slots.get(ch));
		}

	}

	/**
	 * @return Whether the items in this GUI can be stolen by default.
	 * It's important to note that items with consumers can <b>never</b> be stolen, regardless of this setting.
	 */
	public boolean isStealable() {
		return stealableItems;
	}

	/**
	 * @return Whether the given slot in this GUI can have its item stolen.
	 * Will return true if {@link #isStealable()} is true and the slot does not have a click event consumer associated with it.
	 */
	public boolean isStealable(Character slot) {
		return stealableSlots.contains(slot) || (stealableItems && slots.get(slot) != null);
	}

	/**
	 * @param stealableItems Whether items in this GUI should be stealable by default.
	 */
	public void setStealable(boolean stealableItems) {
		this.stealableItems = stealableItems;
	}

	/**
	 * Sets the consumer to be ran when this GUI is opened.
	 * @param onOpen The consumer to be ran when this GUI is opened.
	 */
	public void setOnOpen(Consumer<InventoryOpenEvent> onOpen) {
		this.onOpen = onOpen;
	}

	/**
	 * Sets the consumer to be ran when this GUI is closed.
	 * @param onClose The consumer to be ran when this GUI is closed.
	 */
	public void setOnClose(Consumer<InventoryCloseEvent> onClose) {
		this.onClose = onClose;
	}

	/**
	 * Sets whether this GUI's close event should be cancelled.
	 * @param cancel Whether this GUI's close event should be cancelled.
	 */
	public void setCloseCancelled(boolean cancel) {
		closeCancelled = cancel;
	}

	/**
	 * @return The ID of this GUI if it is a global GUI
	 * @see GUIManager
	 */
	@Nullable
	public String getID() {
		return id;
	}

	/**
	 * Updates the ID of this GUI. Updates will be made in the {@link GUIManager} too.
	 * @param id The new id for this GUI. If null, it will be removed from the {@link GUIManager} and cleared unless it has viewers.
	 */
	public void setID(@Nullable String id) {
		this.id = id;
		if (id == null && inventory.getViewers().size() == 0) {
			SkriptGUI.getGUIManager().unregister(this);
			clear();
		}
	}

}