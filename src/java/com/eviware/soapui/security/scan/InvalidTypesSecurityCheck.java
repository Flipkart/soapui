/*
 *  soapUI, copyright (C) 2004-2011 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.security.scan;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.schema.SchemaTypeImpl;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.InvalidSecurityCheckConfig;
import com.eviware.soapui.config.SchemaTypeForSecurityCheckConfig;
import com.eviware.soapui.config.SecurityCheckConfig;
import com.eviware.soapui.config.StrategyTypeConfig;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.MessageExchange;
import com.eviware.soapui.model.security.SecurityCheckedParameter;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.security.SecurityTestRunContext;
import com.eviware.soapui.security.SecurityTestRunner;
import com.eviware.soapui.security.boundary.BoundaryRestrictionUtill;
import com.eviware.soapui.security.ui.InvalidTypesTable;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.types.StringToStringMap;
import com.eviware.soapui.support.xml.XmlObjectTreeModel;
import com.eviware.soapui.support.xml.XmlUtils;
import com.eviware.soapui.support.xml.XmlObjectTreeModel.XmlTreeNode;

public class InvalidTypesSecurityCheck extends AbstractSecurityScanWithProperties
{

	public final static String TYPE = "InvalidTypesSecurityCheck";
	public final static String NAME = "Invalid Types";

	private InvalidTypesForSOAP invalidTypes;
	private TypeLabel typeLabel = new TypeLabel();
	private InvalidSecurityCheckConfig invalidTypeConfig;

	private Map<SecurityCheckedParameter, ArrayList<String>> parameterMutations = new HashMap<SecurityCheckedParameter, ArrayList<String>>();

	private boolean mutation;

	public InvalidTypesSecurityCheck( TestStep testStep, SecurityCheckConfig config, ModelItem parent, String icon )
	{
		super( testStep, config, parent, icon );

		if( config.getConfig() == null || !( config.getConfig() instanceof InvalidSecurityCheckConfig ) )
			initInvalidTypesConfig();
		else
			invalidTypeConfig = ( InvalidSecurityCheckConfig )config.getConfig();

	}

	@Override
	public void updateSecurityConfig( SecurityCheckConfig config )
	{
		super.updateSecurityConfig( config );

		if( invalidTypeConfig != null )
		{
			invalidTypeConfig = ( InvalidSecurityCheckConfig )getConfig().getConfig();
		}
	}

	public InvalidSecurityCheckConfig getInvalidTypeConfig()
	{
		if( invalidTypeConfig == null || getConfig().getConfig() == null
				|| !( getConfig().getConfig() instanceof InvalidSecurityCheckConfig ) )
			initInvalidTypesConfig();
		return invalidTypeConfig;
	}

	private void initInvalidTypesConfig()
	{
		getConfig().setConfig( InvalidSecurityCheckConfig.Factory.newInstance() );
		invalidTypeConfig = ( InvalidSecurityCheckConfig )getConfig().getConfig();
		invalidTypes = new InvalidTypesForSOAP();

		// add all types..
		for( int key : invalidTypes.getDefaultTypeMap().keySet() )
		{
			SchemaTypeForSecurityCheckConfig newType = invalidTypeConfig.addNewTypesList();
			newType.setValue( invalidTypes.getDefaultTypeMap().get( key ) );
			newType.setType( key );
		}
	}

	@Override
	public JComponent getAdvancedSettingsPanel()
	{
		return new InvalidTypesTable( getInvalidTypeConfig() );
	}

	/*
	 * There is no advanced settings/special for this security check
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.eviware.soapui.security.check.AbstractSecurityCheck#getComponent()
	 */
	@Override
	public JComponent getComponent()
	{
		JPanel panel = UISupport.createEmptyPanel( 5, 75, 0, 5 );
		panel.add( typeLabel.getJLabel(), BorderLayout.CENTER );
		return panel;
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	@Override
	public boolean isConfigurable()
	{
		return true;
	}

	@Override
	protected void execute( SecurityTestRunner securityTestRunner, TestStep testStep, SecurityTestRunContext context )
	{
		try
		{
			StringToStringMap updatedParams = updateRequestContent( testStep, context );
			MessageExchange message = ( MessageExchange )testStep.run( ( TestCaseRunner )securityTestRunner, context );

			createMessageExchange( updatedParams, message, context );
		}
		catch( XmlException e )
		{
			SoapUI.logError( e, "[InvalidtypeSecurityScan]XPath seems to be invalid!" );
			reportSecurityScanException( "Property value is not XML or XPath is wrong!" );
		}
		catch( Exception e )
		{
			SoapUI.logError( e, "[InvalidtypeSecurityScan]Property value is not valid xml!" );
			reportSecurityScanException( "Property value is not XML or XPath is wrong!" );
		}
	}

	/*
	 * Set new value for request
	 */
	private StringToStringMap updateRequestContent( TestStep testStep, SecurityTestRunContext context )
			throws XmlException, Exception
	{

		StringToStringMap params = new StringToStringMap();

		if( parameterMutations.size() == 0 )
			mutateParameters( testStep, context );
		if( getExecutionStrategy().getStrategy() == StrategyTypeConfig.ONE_BY_ONE )
		{
			/*
			 * Idea is to drain for each parameter mutations.
			 */
			for( SecurityCheckedParameter param : getParameterHolder().getParameterList() )
			{
				if( parameterMutations.containsKey( param ) )
					if( parameterMutations.get( param ).size() > 0 )
					{

						TestProperty property = getTestStep().getProperties().get( param.getName() );
						String value = context.expand( property.getValue() );
						if( param.getXpath() == null || param.getXpath().trim().length() == 0 )
						{
							testStep.getProperties().get( param.getName() )
									.setValue( parameterMutations.get( param ).get( 0 ) );
							params.put( param.getLabel(), parameterMutations.get( param ).get( 0 ) );
							parameterMutations.get( param ).remove( 0 );
						}
						else
						{
							// no value, do nothing.
							if( value == null || value.trim().equals( "" ) )
								continue;
							if( XmlUtils.seemsToBeXml( value ) )
							// try
							{
								XmlObjectTreeModel model = new XmlObjectTreeModel( property.getSchemaType().getTypeSystem(),
										XmlObject.Factory.parse( value ) );
								XmlTreeNode[] nodes = model.selectTreeNodes( context.expand( param.getXpath() ) );
								for( XmlTreeNode node : nodes )
									node.setValue( 1, parameterMutations.get( param ).get( 0 ) );
								params.put( param.getLabel(), parameterMutations.get( param ).get( 0 ) );
								parameterMutations.get( param ).remove( 0 );

								testStep.getProperties().get( param.getName() ).setValue( model.getXmlObject().toString() );

							}
						}
						break;
					}
			}
		}
		else
		{
			for( TestProperty property : testStep.getPropertyList() )
			{

				String value = context.expand( property.getValue() );
				if( XmlUtils.seemsToBeXml( value ) )
				{
					XmlObjectTreeModel model = null;
					model = new XmlObjectTreeModel( property.getSchemaType().getTypeSystem(), XmlObject.Factory
							.parse( value ) );
					for( SecurityCheckedParameter param : getParameterHolder().getParameterList() )
					{
						if( param.getXpath() == null || param.getXpath().trim().length() == 0 )
						{
							testStep.getProperties().get( param.getName() )
									.setValue( parameterMutations.get( param ).get( 0 ) );
							params.put( param.getLabel(), parameterMutations.get( param ).get( 0 ) );
							parameterMutations.get( param ).remove( 0 );
						}
						else
						{
							// no value, do nothing.
							if( value == null || value.trim().equals( "" ) )
								continue;
							if( param.getName().equals( property.getName() ) )
							{
								XmlTreeNode[] nodes = model.selectTreeNodes( context.expand( param.getXpath() ) );
								if( parameterMutations.containsKey( param ) )
									if( parameterMutations.get( param ).size() > 0 )
									{
										for( XmlTreeNode node : nodes )
											node.setValue( 1, parameterMutations.get( param ).get( 0 ) );
										params.put( param.getLabel(), parameterMutations.get( param ).get( 0 ) );
										parameterMutations.get( param ).remove( 0 );
									}
							}
						}
					}
					if( model != null )
						property.setValue( model.getXmlObject().toString() );

				}
			}
		}

		return params;
	}

	/**
	 * generate set of requests with all variations
	 * 
	 * @param testStep
	 * @param context
	 * @throws Exception
	 * @throws XmlException
	 * 
	 * @throws XmlException
	 */
	private void mutateParameters( TestStep testStep, SecurityTestRunContext context ) throws XmlException, Exception
	{

		mutation = true;
		// for each parameter
		for( SecurityCheckedParameter parameter : getParameterHolder().getParameterList() )
		{

			if( parameter.isChecked() )
			{
				TestProperty property = getTestStep().getProperties().get( parameter.getName() );
				// no xpath, just put invalid type value in parameter value
				if( parameter.getXpath() == null || parameter.getXpath().trim().length() == 0 )
				{
					for( SchemaTypeForSecurityCheckConfig invalidType : invalidTypeConfig.getTypesListList() )
					{

						if( !parameterMutations.containsKey( parameter ) )
							parameterMutations.put( parameter, new ArrayList<String>() );
						parameterMutations.get( parameter ).add( invalidType.getValue() );

					}
				}
				else
				{
					// we have xpath but do we have xml which need to mutate
					// ignore if there is no value, since than we'll get exception
					if( property.getValue() == null && property.getDefaultValue() == null )
						continue;
					// get value of that property
					String value = context.expand( property.getValue() );

					if( XmlUtils.seemsToBeXml( value ) )
					{

						XmlObjectTreeModel model = new XmlObjectTreeModel( property.getSchemaType().getTypeSystem(),
								XmlObject.Factory.parse( value ) );

						XmlTreeNode[] nodes = model.selectTreeNodes( context.expand( parameter.getXpath() ) );

						// for each invalid type set all nodes
						List<SchemaTypeForSecurityCheckConfig> invalidTypes = invalidTypeConfig.getTypesListList();

						for( SchemaTypeForSecurityCheckConfig type : invalidTypes )
						{

							if( nodes.length > 0 )
							{
								if( nodes[0].getSchemaType().getBuiltinTypeCode() != type.getType() )
								{
									if( !parameterMutations.containsKey( parameter ) )
										parameterMutations.put( parameter, new ArrayList<String>() );
									parameterMutations.get( parameter ).add( type.getValue() );
								}
							}

						}
					}
				}
			}
		}
	}

	@Override
	protected boolean hasNext( TestStep testStep, SecurityTestRunContext context )
	{
		boolean hasNext = false;
		if( ( parameterMutations == null || parameterMutations.size() == 0 ) && !mutation )
		{
			if( getParameterHolder().getParameterList().size() > 0 )
				hasNext = true;
			else
				hasNext = false;
		}
		else
		{
			for( SecurityCheckedParameter param : parameterMutations.keySet() )
			{
				if( parameterMutations.get( param ).size() > 0 )
				{
					hasNext = true;
					break;
				}
			}
		}
		if( !hasNext )
		{
			parameterMutations.clear();
			mutation = false;
		}
		return hasNext;
	}

	/**
	 * 
	 * This is support class that should keep track of all simple types. Also it
	 * should provide values for creating invalid requests.
	 * 
	 * @author robert
	 * 
	 */
	private class InvalidTypesForSOAP
	{

		private HashMap<Integer, String> typeMap = new HashMap<Integer, String>();

		public InvalidTypesForSOAP()
		{
			generateInvalidTypes();
		}

		/*
		 * see http://www.w3.org/TR/xmlschema-0/#CreatDt
		 */
		private void generateInvalidTypes()
		{

			// strings
			typeMap.put( SchemaType.BTC_STRING, "SoapUI is\t the\r best\n" );
			// no cr/lf/tab
			typeMap.put( SchemaType.BTC_NORMALIZED_STRING, "SoapUI is the best" );
			// no cr/lf/tab
			typeMap.put( SchemaType.BTC_TOKEN, "SoapUI is the best" );
			// base64Binary
			typeMap.put( SchemaType.BTC_BASE_64_BINARY, "GpM7" );
			// hexBinary
			typeMap.put( SchemaType.BTC_HEX_BINARY, "0FB7" );
			// integer - no min or max
			typeMap.put( SchemaType.BTC_INTEGER, "-1267896799" );
			// positive integer
			typeMap.put( SchemaType.BTC_POSITIVE_INTEGER, "1267896799" );
			// negative integer
			typeMap.put( SchemaType.BTC_NEGATIVE_INTEGER, "-1" );
			// non negative integer
			typeMap.put( SchemaType.BTC_NON_NEGATIVE_INTEGER, "1" );
			// non positive integer
			typeMap.put( SchemaType.BTC_NON_POSITIVE_INTEGER, "0" );
			// long
			typeMap.put( SchemaType.BTC_LONG, "-882223334991111111" );
			// unsigned long
			typeMap.put( SchemaType.BTC_UNSIGNED_LONG, "882223334991111111" );
			// int
			typeMap.put( SchemaType.BTC_INT, "-2147483647" );
			// unsigned int
			typeMap.put( SchemaType.BTC_UNSIGNED_INT, "294967295" );
			// short
			typeMap.put( SchemaType.BTC_SHORT, "-32768" );
			// unsigned short
			typeMap.put( SchemaType.BTC_UNSIGNED_SHORT, "65535" );
			// byte
			typeMap.put( SchemaType.BTC_BYTE, "127" );
			// unsigned byte
			typeMap.put( SchemaType.BTC_UNSIGNED_BYTE, "255" );
			// decimal
			typeMap.put( SchemaType.BTC_DECIMAL, "-1.23" );
			// float
			typeMap.put( SchemaType.BTC_FLOAT, "-1E4f" );
			// double
			typeMap.put( SchemaType.BTC_DOUBLE, "12.45E+12" );
			// boolean
			typeMap.put( SchemaType.BTC_BOOLEAN, "true" );
			// duration
			typeMap.put( SchemaType.BTC_DURATION, "P1Y2M3DT10H30M12.3S" );
			// date time
			typeMap.put( SchemaType.BTC_DATE_TIME, "1999-05-31T13:20:00.000-05:00" );
			// date
			typeMap.put( SchemaType.BTC_DATE, "1999-05-31" );

			// need to add more...

		}

		public HashMap<Integer, String> getDefaultTypeMap()
		{
			return typeMap;
		}

	}

	@Override
	public String getConfigDescription()
	{
		return "Configures invalid type security scan";
	}

	@Override
	public String getConfigName()
	{
		return "Invalid Types Security Scan";
	}

	@Override
	public String getHelpURL()
	{
		return "http://soapui.org/Security/invalid-types.html";
	}

	@Override
	protected void clear()
	{
		parameterMutations.clear();
		mutation = false;
	}

	public class TypeLabel
	{
		private String text = "<html><pre>    </pre></html>";
		private JLabel jlabel = new JLabel();
		{
			setJlabel( text );
		}

		public void setJlabel( String text )
		{
			text = text.replace( "[", "" );
			text = text.replace( "]", "" );
			jlabel.setText( text );
		}

		public JLabel getJLabel()
		{
			return jlabel;
		}

	}

	public TypeLabel getTypeLabel()
	{
		return typeLabel;
	}

	public void refreshRestrictionLabel( int row )
	{
		if( row == -1 )
		{
			typeLabel.setJlabel( "- no parameter selected -" );
			return;
		}
		SecurityCheckedParameter parameter = getParameterAt( row );
		if( parameter == null )
		{
			return;
		}
		String name = parameter.getName();
		String xpath = parameter.getXpath();
		TestProperty tp = getTestStep().getProperty( name );
		XmlObjectTreeModel xmlObjectTreeModel = null;
		if( tp.getSchemaType() != null && XmlUtils.seemsToBeXml( tp.getValue() ) )
		{
			try
			{
				xmlObjectTreeModel = new XmlObjectTreeModel( tp.getSchemaType().getTypeSystem(), XmlObject.Factory
						.parse( tp.getValue() ) );
			}
			catch( XmlException e )
			{
				SoapUI.logError( e );
			}

			XmlTreeNode[] treeNodes = xmlObjectTreeModel.selectTreeNodes( xpath );

			if( treeNodes.length == 0 )
			{
				typeLabel.setJlabel( "" );
				return;
			}

			SchemaTypeImpl simpleType = ( SchemaTypeImpl )treeNodes[0].getSchemaType();
			if( simpleType != null && !simpleType.isNoType() )
			{
				XmlObjectTreeModel model2 = new XmlObjectTreeModel( simpleType.getTypeSystem(), simpleType.getParseObject() );
				List<String> list = BoundaryRestrictionUtill.getType( model2.getRootNode(), new ArrayList<String>() );
				if( list.isEmpty() )
					typeLabel.setJlabel( "parameter has type [" + simpleType.getName() + "]" );
				else
					typeLabel.setJlabel( "parameter has types [" + list.toString() + "]" );
			}
			else
			{
				typeLabel.setJlabel( "parameter is missing type in schema" );
			}
		}
		else
		{
			typeLabel.setJlabel( "- no parameter selected ->" );
		}

	}

}