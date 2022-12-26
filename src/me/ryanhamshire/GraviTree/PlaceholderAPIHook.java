package me.ryanhamshire.GraviTree;

import org.bukkit.entity.Player;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PlaceholderAPIHook extends PlaceholderExpansion {
	
	private GraviTree plugin;
	
	public PlaceholderAPIHook(GraviTree tree) {
		plugin = tree;
	}
	
    @Override
    public boolean persist(){
        return true;
    }  

   @Override
   public boolean canRegister(){
       return true;
   }

   @Override
   public String getAuthor(){
       return plugin.getDescription().getAuthors().toString();
   }

	@Override
	public String getIdentifier(){
		return "GraviTree";
	}

	@Override
	public String getVersion(){
		return plugin.getDescription().getVersion();
	}

	@Override
	public String onPlaceholderRequest(Player player, String identifier){	
		if(identifier.equals("chop_enabled")){
			PlayerData playerData = PlayerData.FromPlayer(player);
            return Boolean.toString(playerData.isChopEnabled());
		}
		return null;
	}
	
}
