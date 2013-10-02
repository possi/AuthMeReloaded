package uk.org.whoami.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;

import uk.org.whoami.authme.api.API;

/**
*
* @author Xephi59
*/
public class ResetInventoryEvent extends CustomEvent implements Cancellable {

	private Player player;

	public ResetInventoryEvent(Player player) {
		this.player = player;
	}

	public Player getPlayer() {
		return this.player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

}
