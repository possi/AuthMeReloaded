package uk.org.whoami.authme.events;

import org.bukkit.entity.Player;

/**
*
* @author Xephi59
*/
public class LoginEvent extends UncancellableEvent {

	private Player player;
	private boolean isLogin;

	public LoginEvent(Player player, boolean isLogin) {
		this.player = player;
		this.isLogin = isLogin;
	}

	public Player getPlayer() {
		return this.player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	@Deprecated // Doesn't make sense to modify the event, if the result is never processed
	public void setLogin(boolean isLogin) {
		this.isLogin = isLogin;
	}

	public boolean isLogin() {
		return isLogin;
	}

}
