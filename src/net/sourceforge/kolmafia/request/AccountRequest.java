/**
 * Copyright (c) 2005-2011, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia.request;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.RequestLogger;
import net.sourceforge.kolmafia.RequestThread;

import net.sourceforge.kolmafia.persistence.AscensionSnapshot;
import net.sourceforge.kolmafia.preferences.Preferences;
import net.sourceforge.kolmafia.utilities.StringUtilities;

public class AccountRequest
	extends PasswordHashRequest
{
	private static final Pattern SELECTED_PATTERN =
		Pattern.compile( "selected=\"selected\" value=\"?(\\d+)\"?>" );

	public static final int ALL = 0;
	public static final int INTERFACE = 1;
	public static final int INVENTORY = 2;
	public static final int CHAT = 3;
	public static final int COMBAT = 4;
	public static final int ACCOUNT = 5;
	public static final int PROFILE = 6;
	public static final int PRIVACY = 7;

	private int tab;

	public AccountRequest()
	{
		this( ALL );
	}

	public AccountRequest( final int tab )
	{
		super( "account.php" );
		this.tab = tab;

		String field = getTabField( tab );
		if ( field != null )
		{
			this.addFormField( "tab", field );
		}
	}

	private static final String getTabField( final int tab )
	{
		switch ( tab )
		{
		case INTERFACE:
			return "interface";
		case INVENTORY:
			return "inventory";
		case CHAT:
			return "chat";
		case COMBAT:
			return "combat";
		case ACCOUNT:
			return "account";
		case PROFILE:
			return "profile";
		case PRIVACY:
			return "privacy";
		}
		return null;
	}

	private static final Pattern TAB_PATTERN =
		Pattern.compile( "tab=([^&]*)" );
	private static final Pattern LOADTAB_PATTERN =
		Pattern.compile( "action=loadtab&value=([^&]*)" );

	private static final int getTab( final String urlString )
	{
		if ( urlString.equals( "account.php" ) )
		{
			return AccountRequest.INTERFACE;
		}

		Matcher m = TAB_PATTERN.matcher( urlString );
		if ( !m.find() )
		{
			m = LOADTAB_PATTERN.matcher( urlString );
			if ( !m.find() )
			{
				return -1;
			}
		}

		String tabName = m.group(1);
		if ( tabName.equals( "interface" ) )
		{
			return INTERFACE;
		}
		if ( tabName.equals( "inventory" ) )
		{
			return INVENTORY;
		}
		if ( tabName.equals( "chat" ) )
		{
			return CHAT;
		}
		if ( tabName.equals( "combat" ) )
		{
			return COMBAT;
		}
		if ( tabName.equals( "account" ) )
		{
			return ACCOUNT;
		}
		if ( tabName.equals( "profile" ) )
		{
			return PROFILE;
		}
		if ( tabName.equals( "privacy" ) )
		{
			return PRIVACY;
		}

		return -1;
	}

	protected boolean retryOnTimeout()
	{
		return true;
	}

	public Object run()
	{
		if ( this.tab == ALL )
		{
			RequestThread.postRequest( new AccountRequest ( INTERFACE ) );
			RequestThread.postRequest( new AccountRequest ( INVENTORY ) );
			// RequestThread.postRequest( new AccountRequest ( CHAT ) );
			RequestThread.postRequest( new AccountRequest ( COMBAT ) );
			RequestThread.postRequest( new AccountRequest ( ACCOUNT ) );
			// RequestThread.postRequest( new AccountRequest ( PROFILE ) );
			// RequestThread.postRequest( new AccountRequest ( PRIVACY ) );
			return null;
		}

		super.run();
		return null;
	}

	public void processResults()
	{
		AccountRequest.parseAccountData( this.getURLString(), this.responseText );
	}

	public static final void parseAccountData( final String location, final String responseText )
	{
		if ( location.indexOf( "&ajax" ) != -1 )
		{
			AccountRequest.parseAjax( location );
			return;
		}

		PasswordHashRequest.updatePasswordHash( responseText );

		switch ( AccountRequest.getTab( location ) )
		{
		case INTERFACE:
			AccountRequest.parseInterfaceOptions( responseText );
			return;
		case INVENTORY:
			AccountRequest.parseInventoryOptions( responseText );
			return;
		case CHAT:
			AccountRequest.parseChatOptions( responseText );
			return;
		case COMBAT:
			AccountRequest.parseCombatOptions( responseText );
			return;
		case ACCOUNT:
			AccountRequest.parseAccountOptions( responseText );
			return;
		case PROFILE:
			AccountRequest.parseProfileOptions( responseText );
			return;
		case PRIVACY:
			AccountRequest.parsePrivacyOptions( responseText );
			return;
		}
	}

	private static boolean getCheckbox( final String flag, final String responseText )
	{
		String test = "checked=\"checked\"  name=\"" + flag + "\"";
		return responseText.indexOf( test ) != -1;
	}

	private static String fancyMenuStyle = "<input type=\"radio\" value=\"fancy\" checked=\"checked\"  name=\"menu\"/>Icons";
	private static String compactMenuStyle = "<input type=\"radio\" value=\"compact\" checked=\"checked\"  name=\"menu\"/>Drop-Downs";
	private static String normalMenuStyle = "<input type=\"radio\" value=\"normal\" checked=\"checked\"  name=\"menu\"/>Links";

	private static final void parseInterfaceOptions( final String responseText )
	{
		// Top Menu Style
		GenericRequest.topMenuStyle =
			responseText.indexOf( fancyMenuStyle ) != -1 ?
			GenericRequest.MENU_FANCY :
			responseText.indexOf( compactMenuStyle ) != -1 ?
			GenericRequest.MENU_COMPACT :
			GenericRequest.MENU_NORMAL;

		// Remember if the sidepane is in compact mode
		GenericRequest.compactCharacterPane = AccountRequest.getCheckbox( "flag_compactchar", responseText );
	}

	private static final void parseInventoryOptions( final String responseText )
	{
		boolean checked = AccountRequest.getCheckbox( "flag_sellstuffugly", responseText );
		KoLCharacter.setAutosellMode( checked ? "compact" : "detailed" );
		checked = AccountRequest.getCheckbox( "flag_unfamequip", responseText );
		KoLCharacter.setUnequipFamiliar( checked );
	}

	private static final void parseChatOptions( final String responseText )
	{
	}

	private static final Pattern AUTOATTACK_PATTERN =
		Pattern.compile( "<select name=\"autoattack\">.*?</select>", Pattern.DOTALL );

	private static final void parseCombatOptions( final String responseText )
	{
		// Disable stationary buttons to avoid conflicts when
		// the action bar is enabled.

		boolean checked = AccountRequest.getCheckbox( "flag_wowbar", responseText );
		Preferences.setBoolean( "serverAddsCustomCombat", checked );

		int autoAttackAction = 0;

		Matcher selectMatcher = AccountRequest.AUTOATTACK_PATTERN.matcher( responseText );
		if ( selectMatcher.find() )
		{
			Matcher optionMatcher = AccountRequest.SELECTED_PATTERN.matcher( selectMatcher.group() );
			if ( optionMatcher.find() )
			{
				String autoAttackActionString = optionMatcher.group( 1 );
				autoAttackAction = Integer.parseInt( autoAttackActionString );
			}
		}

		KoLCharacter.setAutoAttackAction( autoAttackAction );
	}

	private static final void parseAccountOptions( final String responseText )
	{
		// Consumption restrictions are also found here through the
		// presence of buttons.
		//
		// <input class=button name="action" type="submit" value="Drop Oxygenarian">

		int path = 
			responseText.indexOf( "<input class=button name=\"action\" type=submit value=\"Drop Oxygenarian\">" ) != -1 ?
			AscensionSnapshot.OXYGENARIAN :
			responseText.indexOf( "<input class=button name=\"action\" type=submit value=\"Drop Boozetafarian\">" ) != -1 ?
			AscensionSnapshot.BOOZETAFARIAN :
			responseText.indexOf( "<input class=button name=\"action\" type=submit value=\"Drop Teetotaler\">" ) != -1 ?
			AscensionSnapshot.TEETOTALER :
			AscensionSnapshot.NOPATH;

		KoLCharacter.setConsumptionRestriction( path );

		// Whether or not a player is currently in Bad Moon or hardcore
		// is also found here through the presence of buttons.

		// <input class=button name="action" type=submit value="Drop Hardcore">
		boolean isHardcore = responseText.indexOf( "<input class=button name=\"action\" type=submit value=\"Drop Hardcore\">" ) != -1;
		KoLCharacter.setHardcore( isHardcore );

		// <input class=button name="action" type=submit value="Drop Bad Moon">

		if ( responseText.indexOf( "<input class=button name=\"action\" type=submit value=\"Drop Bad Moon\">" ) != -1 )
		{
			KoLCharacter.setHardcore( true );
			KoLCharacter.setSign( "Bad Moon" );
		}
		else if ( KoLCharacter.getSignStat() == KoLConstants.BAD_MOON )
		{
			KoLCharacter.setSign( "None" );
		}

		// Your skills have been recalled if you have freed the king
		// and don't have a "Recall Skills" button in your account menu
		boolean recalled =
			KoLCharacter.kingLiberated() &&
			responseText.indexOf( "<input class=button name=\"action\" type=submit value=\"Recall Skills\">") == -1;
		KoLCharacter.setSkillsRecalled( recalled );
	}

	private static final void parseProfileOptions( final String responseText )
	{
	}

	private static final void parsePrivacyOptions( final String responseText )
	{
	}

	private static final Pattern AJAX_ACTION_PATTERN =
		Pattern.compile( "action=([^&]*)");
	private static final Pattern AJAX_VALUE_PATTERN =
		Pattern.compile( "value=([^&]*)");

	private static final void parseAjax( final String location )
	{
		Matcher actionMatcher = AccountRequest.AJAX_ACTION_PATTERN.matcher( location );
		if ( !actionMatcher.find() )
		{
			return;
		}

		Matcher valueMatcher = AccountRequest.AJAX_VALUE_PATTERN.matcher( location );
		if ( !valueMatcher.find() )
		{
			return;
		}

		String action = actionMatcher.group(1);
		String valueString = valueMatcher.group(1);

		// Interface options

		if ( action.equals( "menu" ) )
		{
			// account.php?pwd&action=menu&value=fancy&ajax=1
			// account.php?pwd&action=menu&value=compact&ajax=1
			// account.php?pwd&action=menu&value=normal&ajax=1
			GenericRequest.topMenuStyle =
				valueString.equals( "fancy" ) ?
				GenericRequest.MENU_FANCY :
				valueString.equals( "compact" ) ?
				GenericRequest.MENU_COMPACT :
				GenericRequest.MENU_NORMAL;
			return;
		}

		if ( action.equals( "flag_compactchar" ) )
		{
			boolean checked = valueString.equals( "1" );
			GenericRequest.compactCharacterPane = checked;
			return;
		}

		// Inventory options

		if ( action.equals( "flag_sellstuffugly" ) )
		{
			boolean checked = valueString.equals( "1" );
			KoLCharacter.setAutosellMode( checked ? "compact" : "detailed" );
			return;
		}

		if ( action.equals( "flag_unfamequip" ) )
		{
			boolean checked = valueString.equals( "1" );
			KoLCharacter.setUnequipFamiliar( checked );
			return;
		}

		// Combat options

		if ( action.equals( "flag_wowbar" ) )
		{
			boolean checked = valueString.equals( "1" );
			Preferences.setBoolean( "serverAddsCustomCombat", checked );
			return;
		}

		if ( action.equals( "autoattack" ) )
		{
			int value = Integer.parseInt( valueString );
			KoLCharacter.setAutoAttackAction( value );
			return;
		}

		// Account options

		// <form method="post" action="account.php">

		// <input type=hidden name="actions[]" value="unronin">
		// <input class=button name="action" type=submit value="Forsake Ronin">
		// <input type="checkbox" class="confirm" name="unroninconfirm" value="1">

		if ( action.equals( "unronin" ) )
		{
			// Dropping from Softcore to Casual.
			return;
		}

		// <input type=hidden name="actions[]" value="unpath">
		// <input class=button name="action" type="submit" value="Drop Oxygenarian">
		// <input type="checkbox" class="confirm" name="unpathconfirm" value="1">

		if ( action.equals( "unpath" ) )
		{
			// Dropping consumption restrictions
			KoLCharacter.setConsumptionRestriction( AscensionSnapshot.NOPATH );
			RequestLogger.updateSessionLog();
			RequestLogger.updateSessionLog( "dropped consumption restrictions" );
			RequestLogger.updateSessionLog();
			return;
		}

		// <input type=hidden name="actions[]" value="unhardcore">
		// <input class=button name="action" type=submit value="Drop Hardcore">
		// <input type="checkbox" class="confirm" name="unhardcoreconfirm" value="1">

		if ( action.equals( "unhardcore" ) )
		{
			// Dropping Hardcore
			KoLCharacter.setHardcore( false );
			RequestLogger.updateSessionLog();
			RequestLogger.updateSessionLog( "dropped hardcore. Wimp." );
			RequestLogger.updateSessionLog();
			return;
		}

		// <input type=hidden name="actions[]" value="unbadmoon">
		// <input class=button name="action" type=submit value="Drop Bad Moon">
		// <input type="checkbox" class="confirm" name="unbadmoonconfirm" value="1">

		if ( action.equals( "unbadmoon" ) )
		{
			// Dropping Bad Moon
			KoLCharacter.setSign( "None" );
			RequestLogger.updateSessionLog();
			RequestLogger.updateSessionLog( "dropped Bad Moon. Fool." );
			RequestLogger.updateSessionLog();
			return;
		}

		if ( action.equals( "recallskills" ) )
		{
			if ( location.indexOf( "confirm=on" ) != -1 )
			{
				KoLCharacter.setSkillsRecalled( true );
			}
			return;
		}
	}
}
